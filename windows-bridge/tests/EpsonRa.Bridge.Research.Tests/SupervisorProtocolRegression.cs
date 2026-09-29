using System;
using System.Diagnostics;
using System.IO;
using EpsonRa.Bridge.Research.Supervisor;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class SupervisorProtocolRegression
    {
        public static int Main(string[] args)
        {
            if (args.Length > 0 && args[0] == "--request")
            {
                var mode = File.ReadAllText(args[1]);
                if (mode == "flood") Console.Write(new string('x', 1024 * 1024 + 1));
                else if (mode == "contradiction") Console.Write("{\"schemaVersion\":1,\"status\":\"FAILED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                else if (mode == "normal") Console.Write("{\"schemaVersion\":1,\"status\":\"COMPLETED\",\"success\":true,\"cleanup\":\"CONFIRMED\"}");
                return 0;
            }
            if (args.Length != 0 && (args.Length != 2 || args[0] != "--test-fixture")) return 64;
            var failed = 0;
            foreach (var mode in args.Length == 2 ? new[] { "normal", "flood", "contradiction", "absent", "nonzero", "malformed", "null", "array", "multiple", "failed-cleanup", "stderr", "wrong-schema", "timeout", "inherited" } : new[] { "normal", "flood", "contradiction", "absent" })
            {
                try { Check(mode, args.Length == 2 ? args[1] : typeof(SupervisorProtocolRegression).Assembly.Location, args.Length == 2); Console.WriteLine("PASS " + mode); }
                catch (Exception e) { failed++; Console.WriteLine("FAIL " + mode + ": " + e.Message); }
            }
            return failed == 0 ? 0 : 1;
        }

        public static void Check(string mode, string worker, bool fixture = false)
        {
            var path = Path.GetTempFileName();
            try {
                File.WriteAllText(path, mode);
                var watch = Stopwatch.StartNew();
                var result = WorkerSupervisor.Run(new WorkerRequest { WorkerPath = worker, RequestPath = path, TimeoutSeconds = 2, ExtraArguments = fixture ? new[] { mode } : null });
                if (watch.Elapsed.TotalSeconds > 4) throw new Exception("deadline exceeded");
                if (mode == "normal") {
                    if (!result.Success || result.ExitCode != 0) throw new Exception("request protocol/stdout result not accepted");
                } else if (result.Success || result.ExitCode != (mode == "timeout" || mode == "inherited" ? 124 : 3)) throw new Exception("invalid result must fail");
                if (mode == "flood" && result.Error != "OutputLimit") throw new Exception("flood must hit output cap");
                if (mode == "contradiction" && result.Error != "WorkerReportedFailure") throw new Exception("contradiction must reach result validation");
            } finally { File.Delete(path); }
        }
    }
}
