using System;
using System.IO;
using EpsonRa.Bridge.Research;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class PeImageInspectorTests
    {
        [TestMethod]
        public void IlOnlyI386Without32BitRequirementIsAnyCpu()
        {
            using (var stream = new MemoryStream(PeFixture(0x14c, 0x9)))
            {
                var info = PeImageInspector.Inspect(stream);
                Assert.AreEqual((ushort)0x14c, info.Machine);
                Assert.AreEqual((uint)0x9, info.CorFlags);
                Assert.AreEqual(PeArchitecture.AnyCpu, info.Architecture);
            }
        }

        [TestMethod]
        public void Required32BitFlagSelectsX86()
        {
            using (var stream = new MemoryStream(PeFixture(0x14c, 0x3)))
                Assert.AreEqual(PeArchitecture.X86, PeImageInspector.Inspect(stream).Architecture);
        }

        [TestMethod]
        public void Amd64MachineSelectsX64()
        {
            using (var stream = new MemoryStream(PeFixture(0x8664, 0x1)))
                Assert.AreEqual(PeArchitecture.X64, PeImageInspector.Inspect(stream).Architecture);
        }

        [TestMethod]
        public void OtherMachineIsUnsupported()
        {
            using (var stream = new MemoryStream(PeFixture(0xaa64, 0x1)))
                Assert.AreEqual(PeArchitecture.Unsupported, PeImageInspector.Inspect(stream).Architecture);
        }

        [TestMethod]
        public void TruncatedDosPeAndClrHeadersAreRejected()
        {
            foreach (var length in new[] { 32, 0x84, 0x218 })
            {
                var bytes = PeFixture(0x14c, 0x9);
                Array.Resize(ref bytes, length);
                using (var stream = new MemoryStream(bytes))
                    try
                    {
                        PeImageInspector.Inspect(stream);
                        Assert.Fail("Expected InvalidDataException.");
                    }
                    catch (InvalidDataException)
                    {
                    }
            }
        }

        [TestMethod]
        public void AbsentClrDirectoryAndUnmappedRvaAreRejected()
        {
            var absent = PeFixture(0x14c, 0x9);
            Write32(absent, 0x168, 0);
            var unmapped = PeFixture(0x14c, 0x9);
            Write32(unmapped, 0x168, 0x9000);
            foreach (var bytes in new[] { absent, unmapped })
                using (var stream = new MemoryStream(bytes))
                    try
                    {
                        PeImageInspector.Inspect(stream);
                        Assert.Fail("Expected InvalidDataException.");
                    }
                    catch (InvalidDataException)
                    {
                    }
        }

        [TestMethod]
        public void InspectionReadsOnlyTheSuppliedStream()
        {
            using (var stream = new MemoryStream(PeFixture(0x8664, 0x1)))
            {
                Assert.AreEqual(PeArchitecture.X64, PeImageInspector.Inspect(stream).Architecture);
                Assert.IsTrue(stream.Position > 0);
            }
        }

        private static byte[] PeFixture(ushort machine, uint flags)
        {
            // DOS -> PE at 0x80; PE32 optional header at 0x98; one section maps RVA 0x2000 to file 0x200.
            var bytes = new byte[0x300];
            bytes[0] = (byte)'M'; bytes[1] = (byte)'Z';
            Write32(bytes, 0x3c, 0x80);
            bytes[0x80] = (byte)'P'; bytes[0x81] = (byte)'E';
            Write16(bytes, 0x84, machine);
            Write16(bytes, 0x86, 1);
            Write16(bytes, 0x94, 0xe0);
            Write16(bytes, 0x98, 0x10b);
            Write32(bytes, 0xf4, 16); // NumberOfRvaAndSizes.
            Write32(bytes, 0x168, 0x2000); // CLR directory RVA, index 14.
            Write32(bytes, 0x16c, 0x48);
            Write32(bytes, 0x180, 0x200); // Section virtual size.
            Write32(bytes, 0x184, 0x2000); // Section virtual address.
            Write32(bytes, 0x188, 0x100); // Section raw size.
            Write32(bytes, 0x18c, 0x200); // Section raw pointer.
            Write32(bytes, 0x200, 0x48); // IMAGE_COR20_HEADER.cb.
            Write32(bytes, 0x210, flags);
            return bytes;
        }

        private static void Write16(byte[] bytes, int offset, ushort value)
        {
            bytes[offset] = (byte)value;
            bytes[offset + 1] = (byte)(value >> 8);
        }

        private static void Write32(byte[] bytes, int offset, uint value)
        {
            for (var i = 0; i < 4; i++)
                bytes[offset + i] = (byte)(value >> (8 * i));
        }
    }
}
