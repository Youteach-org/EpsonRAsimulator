using System;
using System.IO;

namespace EpsonRa.Bridge.Research
{
    public enum PeArchitecture
    {
        AnyCpu,
        X86,
        X64,
        Unsupported
    }

    public sealed class PeImageInfo
    {
        public PeImageInfo(ushort machine, uint corFlags, PeArchitecture architecture)
        {
            Machine = machine;
            CorFlags = corFlags;
            Architecture = architecture;
        }

        public ushort Machine { get; }
        public uint CorFlags { get; }
        public PeArchitecture Architecture { get; }
    }

    public static class PeImageInspector
    {
        private const uint PeSignature = 0x00004550;
        private const ushort Pe32Magic = 0x10b;
        private const ushort Pe32PlusMagic = 0x20b;
        private const uint CorFlagIlOnly = 0x1;
        private const uint CorFlag32BitRequired = 0x2;

        public static PeImageInfo Inspect(Stream stream)
        {
            if (stream == null || !stream.CanRead || !stream.CanSeek)
                throw new InvalidDataException("A readable seekable stream is required.");

            var length = stream.Length;
            if (length < 0x40)
                throw new InvalidDataException("Truncated DOS header.");
            if (ReadUInt16(stream, 0) != 0x5a4d)
                throw new InvalidDataException("Missing MZ header.");

            var peOffset = ReadUInt32(stream, 0x3c);
            EnsureRange(length, peOffset, 24);
            if (ReadUInt32(stream, peOffset) != PeSignature)
                throw new InvalidDataException("Missing PE signature.");

            var coffOffset = checked((long)peOffset + 4);
            var machine = ReadUInt16(stream, coffOffset);
            var sectionCount = ReadUInt16(stream, coffOffset + 2);
            var optionalSize = ReadUInt16(stream, coffOffset + 16);
            var optionalOffset = checked(coffOffset + 20);
            EnsureRange(length, optionalOffset, optionalSize);

            var magic = ReadUInt16(stream, optionalOffset);
            long numberOfRvaAndSizesOffset;
            long dataDirectoryOffset;
            if (magic == Pe32Magic)
            {
                numberOfRvaAndSizesOffset = optionalOffset + 92;
                dataDirectoryOffset = optionalOffset + 96;
            }
            else if (magic == Pe32PlusMagic)
            {
                numberOfRvaAndSizesOffset = optionalOffset + 108;
                dataDirectoryOffset = optionalOffset + 112;
            }
            else
            {
                throw new InvalidDataException("Unsupported optional header.");
            }

            EnsureRange(optionalOffset + optionalSize, numberOfRvaAndSizesOffset, 4);
            if (ReadUInt32(stream, numberOfRvaAndSizesOffset) <= 14)
                throw new InvalidDataException("CLR directory is absent.");

            var clrDirectoryOffset = checked(dataDirectoryOffset + (14L * 8));
            EnsureRange(optionalOffset + optionalSize, clrDirectoryOffset, 8);
            var clrRva = ReadUInt32(stream, clrDirectoryOffset);
            var clrSize = ReadUInt32(stream, clrDirectoryOffset + 4);
            if (clrRva == 0 || clrSize < 0x48)
                throw new InvalidDataException("CLR directory is absent.");

            var sectionTableOffset = checked(optionalOffset + optionalSize);
            EnsureRange(length, sectionTableOffset, checked(sectionCount * 40L));

            long? clrFileOffset = null;
            for (var i = 0; i < sectionCount; i++)
            {
                var sectionOffset = sectionTableOffset + (i * 40L);
                var virtualSize = ReadUInt32(stream, sectionOffset + 8);
                var virtualAddress = ReadUInt32(stream, sectionOffset + 12);
                var rawSize = ReadUInt32(stream, sectionOffset + 16);
                var rawPointer = ReadUInt32(stream, sectionOffset + 20);
                var mappedSize = Math.Max(virtualSize, rawSize);
                var end = (ulong)virtualAddress + mappedSize;
                if ((ulong)clrRva >= virtualAddress && (ulong)clrRva < end)
                {
                    var delta = clrRva - virtualAddress;
                    if (delta >= rawSize || clrSize > (ulong)rawSize - delta)
                        throw new InvalidDataException("CLR RVA is not backed by file data.");
                    clrFileOffset = checked((long)rawPointer + delta);
                    break;
                }
            }

            if (!clrFileOffset.HasValue)
                throw new InvalidDataException("CLR RVA is not mapped.");

            EnsureRange(length, clrFileOffset.Value, clrSize);
            var headerSize = ReadUInt32(stream, clrFileOffset.Value);
            if (headerSize < 0x48 || headerSize > clrSize)
                throw new InvalidDataException("Truncated CLR header.");

            var flags = ReadUInt32(stream, clrFileOffset.Value + 16);
            return new PeImageInfo(machine, flags, Classify(machine, flags));
        }

        private static PeArchitecture Classify(ushort machine, uint flags)
        {
            if (machine == 0x8664)
                return PeArchitecture.X64;
            if (machine != 0x14c)
                return PeArchitecture.Unsupported;
            if ((flags & CorFlag32BitRequired) != 0)
                return PeArchitecture.X86;
            if ((flags & CorFlagIlOnly) != 0)
                return PeArchitecture.AnyCpu;
            return PeArchitecture.Unsupported;
        }

        private static ushort ReadUInt16(Stream stream, long offset)
        {
            var bytes = ReadExact(stream, offset, 2);
            return (ushort)(bytes[0] | (bytes[1] << 8));
        }

        private static uint ReadUInt32(Stream stream, long offset)
        {
            var bytes = ReadExact(stream, offset, 4);
            return (uint)(bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[3] << 24));
        }

        private static byte[] ReadExact(Stream stream, long offset, int count)
        {
            EnsureRange(stream.Length, offset, count);
            stream.Seek(offset, SeekOrigin.Begin);
            var bytes = new byte[count];
            var read = 0;
            while (read < count)
            {
                var n = stream.Read(bytes, read, count - read);
                if (n <= 0)
                    throw new InvalidDataException("Unexpected end of stream.");
                read += n;
            }
            return bytes;
        }

        private static void EnsureRange(long length, long offset, long count)
        {
            if (offset < 0 || count < 0 || offset > length || count > length - offset)
                throw new InvalidDataException("PE structure is truncated or out of range.");
        }
    }
}

