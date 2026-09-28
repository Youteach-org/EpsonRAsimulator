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
            var mode = args.Length > 0 ? args[0] : "normal";
            var resultFile = args.Length > 1 ? args[1] : null;

            if (mode == "normal")
            {
                File.WriteAllText(resultFile, "{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                return 0;
            }

            if (mode == "nonzero")
                return 7;

            if (mode == "malformed")
            {
                File.WriteAllText(resultFile, "{not-json");
                return 0;
            }

            if (mode == "null")
            {
                File.WriteAllText(resultFile, "null");
                return 0;
            }

            if (mode == "array")
            {
                File.WriteAllText(resultFile, "[]");
                return 0;
            }

            if (mode == "multiple")
            {
                File.WriteAllText(resultFile, "{\"schemaVersion\":1} {\"schemaVersion\":1}");
                return 0;
            }

            if (mode == "failed-cleanup")
            {
                File.WriteAllText(resultFile, "{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"FAILED\"}");
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
