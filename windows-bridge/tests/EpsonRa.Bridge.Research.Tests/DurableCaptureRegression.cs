using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Web.Script.Serialization;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class DurableCaptureRegression
    {
        public static int Main(string[] args)
        {
            if (args.Length != 2) return 64;
            try { Check(args[0], args[1]); return 0; }
            catch (Exception e) { Console.WriteLine("FAIL " + e.Message); return 1; }
        }

        // Catches missing durable output, inherited-shell waits, and overwriting prior evidence.
        public static void Check(string supervisor, string worker)
        {
            var root = Path.Combine(Path.GetTempPath(), "epson-capture-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                foreach (var mode in new[] { "normal", "inherited", "timeout" })
                {
                    var request = Path.Combine(root, mode + ".request");
                    var result = Path.Combine(root, mode + ".json");
                    File.WriteAllText(request, mode);
                    var timer = Stopwatch.StartNew();
                    var timeoutSeconds = mode == "normal" ? 10 : 2;
                    var exit = Launch(supervisor, worker, request, result, timeoutSeconds);
                    var expected = mode == "normal" ? 0 : 124;
                    if (exit != expected) throw new Exception(mode + " exit " + exit + " instead of " + expected);
                    if (timer.ElapsedMilliseconds >= 4500) throw new Exception(mode + " capture exceeded deadline");
                    var document = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(File.ReadAllText(result));
                    if ((int)document["ExitCode"] != expected) throw new Exception("Durable result lost exit code");
                    if (mode != "normal" && (string)document["Cleanup"] != "UNKNOWN") throw new Exception("Timeout fabricated cleanup");
                    Console.WriteLine("PASS durable " + mode + " " + timer.ElapsedMilliseconds + "ms");
                }
                var sentinel = Path.Combine(root, "existing.json");
                var blockedRequest = Path.Combine(root, "blocked.request");
                File.WriteAllText(blockedRequest, "normal");
                File.WriteAllText(sentinel, "preserve-evidence");
                if (Launch(supervisor, worker, blockedRequest, sentinel, 2) != 64 || File.ReadAllText(sentinel) != "preserve-evidence")
                    throw new Exception("Existing evidence was not protected");
                if (File.Exists(blockedRequest + ".started")) throw new Exception("Worker launched before rejecting existing evidence");
                Console.WriteLine("PASS existing evidence preserved");
            }
            finally { try { Directory.Delete(root, true); } catch { } }
        }

        private static int Launch(string supervisor, string worker, string request, string result, int timeoutSeconds)
        {
            // ShellExecute starts a hidden, independent console: no redirected/inherited capture pipes.
            using (var process = Process.Start(new ProcessStartInfo {
                FileName = supervisor,
                Arguments = "--worker \"" + worker + "\" --request \"" + request + "\" --timeout-seconds " + timeoutSeconds + " --result-file \"" + result + "\"",
                UseShellExecute = true, WindowStyle = ProcessWindowStyle.Hidden }))
            {
                // Never use the unbounded WaitForExit overload or terminate descendants.
                if (!process.WaitForExit(4500)) throw new Exception("Outer capture deadline expired; owned synthetic process not forcibly terminated");
                return process.ExitCode;
            }
        }
    }
}
