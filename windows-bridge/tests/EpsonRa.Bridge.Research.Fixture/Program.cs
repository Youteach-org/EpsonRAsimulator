using System;
using System.IO;
using System.Text;
using System.Threading;

namespace EpsonRa.Bridge.Research.Fixture
{
    internal static class Program
    {
        private static int Main(string[] args)
        {
            // Finite leaf process: never enters the fixture dispatcher or spawns children.
            if (args.Length == 1 && args[0] == "--hold-stdout") { Thread.Sleep(5000); return 0; }
            var mode = args.Length > 0 ? args[0] : "normal";
            if (args.Length != 5 || args[1] != "--request" || !File.Exists(args[2]) || args[3] != "--events") return 64;

            if (mode == "observed")
            {
                File.WriteAllLines(args[4], new[]
                {
                    "{\"name\":\"before:Load\",\"monotonicTicks\":1}",
                    "{\"name\":\"after:Load\",\"monotonicTicks\":2}",
                    "{\"name\":\"before:Construct\",\"monotonicTicks\":3}",
                    "{\"name\":\"after:Construct\",\"monotonicTicks\":4}",
                    "{\"name\":\"before:SetServerInstance\",\"monotonicTicks\":5}",
                    "{\"name\":\"after:SetServerInstance\",\"monotonicTicks\":6}",
                    "{\"name\":\"before:Initialize\",\"monotonicTicks\":7}",
                    "{\"name\":\"after:Initialize\",\"monotonicTicks\":8}",
                    "{\"name\":\"before:Dispose\",\"monotonicTicks\":9}",
                    "{\"name\":\"after:Dispose\",\"monotonicTicks\":10}"
                });
                Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                return 0;
            }

            if (mode == "normal")
            {
                Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                return 0;
            }

            if (mode == "nonzero")
                return 7;

            if (mode == "absent") return 0;
            if (mode == "valid-flood")
            {
                Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                Console.Write(new string(' ', 1024 * 1024));
                return 0;
            }
            if (mode == "inherited")
            {
                using (var child = System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo {
                    FileName = typeof(Program).Assembly.Location, Arguments = "--hold-stdout",
                    UseShellExecute = false, CreateNoWindow = true })) { }
                Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                return 0;
            }
            if (mode == "contradiction") { Console.Write("{\"schemaVersion\":1,\"status\":\"FAILED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}"); return 0; }
            if (mode == "wrong-schema") { Console.Write("{\"schemaVersion\":true,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}"); return 0; }
            if (mode == "stderr") { Console.Error.Write("synthetic error"); Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}"); return 0; }

            if (mode == "malformed")
            {
                Console.Write("{not-json");
                return 0;
            }

            if (mode == "null")
            {
                Console.Write("null");
                return 0;
            }

            if (mode == "array")
            {
                Console.Write("[]");
                return 0;
            }

            if (mode == "multiple")
            {
                Console.Write("{\"schemaVersion\":1} {\"schemaVersion\":1}");
                return 0;
            }

            if (mode == "failed-cleanup")
            {
                Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"FAILED\"}");
                return 0;
            }

            if (mode == "timeout")
            {
                Thread.Sleep(TimeSpan.FromSeconds(30));
                return 0;
            }

            if (mode == "flood")
            {
                var block = new string('x', 8192);
                for (var i = 0; i < 160; i++)
                    Console.Out.Write(block);
                Thread.Sleep(TimeSpan.FromSeconds(30));
                return 0;
            }

            return 64;
        }
    }
}
