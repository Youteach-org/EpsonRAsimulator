using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;
using Microsoft.Win32.SafeHandles;

namespace EpsonRa.Bridge.Research.DumpCapture
{
    internal static class Program
    {
        private const uint ProcessVmRead = 0x0010;
        private const uint ProcessDupHandle = 0x0040;
        private const uint ProcessQueryInformation = 0x0400;
        private const uint ProcessQueryLimitedInformation = 0x1000;

        [Flags]
        private enum MiniDumpType : uint
        {
            Normal = 0x00000000
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr OpenProcess(uint desiredAccess, bool inheritHandle, int processId);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr handle);

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern bool QueryFullProcessImageName(
            IntPtr processHandle,
            uint flags,
            StringBuilder executableName,
            ref int size);

        [DllImport("Dbghelp.dll", SetLastError = true)]
        private static extern bool MiniDumpWriteDump(
            IntPtr processHandle,
            int processId,
            SafeFileHandle fileHandle,
            MiniDumpType dumpType,
            IntPtr exceptionParam,
            IntPtr userStreamParam,
            IntPtr callbackParam);

        private static int Main(string[] args)
        {
            int pid;
            string expectedImage;
            string dumpPath;
            string parseError;
            if (!TryParse(args, out pid, out expectedImage, out dumpPath, out parseError))
            {
                WriteResult("INVALID_ARGUMENTS", false, parseError, null, null, null);
                return 64;
            }

            var partialDump = false;
            try
            {
                var expectedFull = Path.GetFullPath(expectedImage);
                var dumpFull = Path.GetFullPath(dumpPath);

                if (!File.Exists(expectedFull))
                    throw new InvalidOperationException("ExpectedImageMissing");
                if (File.Exists(dumpFull))
                    throw new InvalidOperationException("DumpPathAlreadyExists");

                var dumpDirectory = Path.GetDirectoryName(dumpFull);
                if (string.IsNullOrEmpty(dumpDirectory) || !Directory.Exists(dumpDirectory))
                    throw new InvalidOperationException("DumpDirectoryMissing");

                using (var process = Process.GetProcessById(pid))
                {
                    if (process.HasExited)
                        throw new InvalidOperationException("TargetAlreadyExited");
                    if (process.Id == Process.GetCurrentProcess().Id)
                        throw new InvalidOperationException("SelfCaptureRejected");

                    var access = ProcessVmRead | ProcessDupHandle | ProcessQueryInformation | ProcessQueryLimitedInformation;
                    var processHandle = OpenProcess(access, false, pid);
                    if (processHandle == IntPtr.Zero)
                        throw new InvalidOperationException("OpenProcessFailed:" + Marshal.GetLastWin32Error().ToString(CultureInfo.InvariantCulture));

                    try
                    {
                        var imageBuffer = new StringBuilder(32768);
                        var imageLength = imageBuffer.Capacity;
                        if (!QueryFullProcessImageName(processHandle, 0, imageBuffer, ref imageLength))
                            throw new InvalidOperationException("TargetImageUnavailable:" + Marshal.GetLastWin32Error().ToString(CultureInfo.InvariantCulture));

                        var actualImage = imageBuffer.ToString();
                        if (string.IsNullOrEmpty(actualImage) ||
                            !string.Equals(Path.GetFullPath(actualImage), expectedFull, StringComparison.OrdinalIgnoreCase))
                            throw new InvalidOperationException("TargetImageMismatch");

                        var flags = MiniDumpType.Normal;

                        using (var stream = new FileStream(dumpFull, FileMode.CreateNew, FileAccess.ReadWrite, FileShare.Read))
                        {
                            partialDump = true;
                            if (!MiniDumpWriteDump(
                                processHandle,
                                pid,
                                stream.SafeFileHandle,
                                flags,
                                IntPtr.Zero,
                                IntPtr.Zero,
                                IntPtr.Zero))
                                throw new InvalidOperationException("MiniDumpWriteDumpFailed:" + Marshal.GetLastWin32Error().ToString(CultureInfo.InvariantCulture));

                            stream.Flush(true);
                        }

                        var info = new FileInfo(dumpFull);
                        if (!info.Exists || info.Length <= 0)
                            throw new InvalidOperationException("EmptyDump");

                        var hash = Sha256(dumpFull);
                        partialDump = false;
                        WriteResult("COMPLETED", true, null, pid, info.Length, hash);
                        return 0;
                    }
                    finally
                    {
                        CloseHandle(processHandle);
                    }
                }
            }
            catch (Exception error)
            {
                if (partialDump)
                {
                    try { File.Delete(Path.GetFullPath(dumpPath)); } catch { }
                }

                WriteResult("FAILED", false, error.Message, pid, null, null);
                return 3;
            }
        }

        private static bool TryParse(
            string[] args,
            out int pid,
            out string expectedImage,
            out string dumpPath,
            out string error)
        {
            pid = 0;
            expectedImage = null;
            dumpPath = null;
            error = null;

            if (args == null || args.Length != 6)
            {
                error = "Expected --pid <pid> --expected-image <absolute-path> --dump <absolute-new-path>";
                return false;
            }

            string pidText = null;
            for (var i = 0; i < args.Length; i += 2)
            {
                var name = args[i];
                var value = args[i + 1];
                if (string.IsNullOrEmpty(value))
                {
                    error = "EmptyArgumentValue";
                    return false;
                }

                if (name == "--pid" && pidText == null) pidText = value;
                else if (name == "--expected-image" && expectedImage == null) expectedImage = value;
                else if (name == "--dump" && dumpPath == null) dumpPath = value;
                else
                {
                    error = "UnknownOrDuplicateArgument:" + name;
                    return false;
                }
            }

            if (!int.TryParse(pidText, NumberStyles.None, CultureInfo.InvariantCulture, out pid) || pid <= 0)
            {
                error = "InvalidPid";
                return false;
            }

            if (string.IsNullOrEmpty(expectedImage) || !Path.IsPathRooted(expectedImage))
            {
                error = "ExpectedImageMustBeAbsolute";
                return false;
            }

            if (string.IsNullOrEmpty(dumpPath) || !Path.IsPathRooted(dumpPath))
            {
                error = "DumpPathMustBeAbsolute";
                return false;
            }

            return true;
        }

        private static string Sha256(string path)
        {
            using (var algorithm = SHA256.Create())
            using (var stream = File.OpenRead(path))
                return BitConverter.ToString(algorithm.ComputeHash(stream)).Replace("-", string.Empty);
        }

        private static void WriteResult(
            string status,
            bool success,
            string error,
            int? pid,
            long? dumpLength,
            string sha256)
        {
            var document = new Dictionary<string, object>
            {
                { "schemaVersion", 1 },
                { "status", status },
                { "success", success },
                { "error", error },
                { "targetPid", pid },
                { "dumpLength", dumpLength },
                { "sha256", sha256 }
            };
            Console.Write(new JavaScriptSerializer().Serialize(document));
        }
    }
}
