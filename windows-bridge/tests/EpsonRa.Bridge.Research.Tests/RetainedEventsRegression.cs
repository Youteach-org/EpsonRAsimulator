using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Web.Script.Serialization;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class RetainedEventsRegression
    {
        public static int Main(string[] args)
        {
            if (args.Length != 2) return 64;
            try { Check(args[0], args[1]); return 0; }
            catch (Exception e) { Console.WriteLine("FAIL " + e.Message); return 1; }
        }

        public static void Check(string supervisor, string worker)
        {
            var root = Path.Combine(Path.GetTempPath(), "epson-events-test-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
            try
            {
                foreach (var mode in new[] { "stage-timeout", "structured-failure" })
                {
                    var request = Path.Combine(root, mode + ".request");
                    var resultPath = Path.Combine(root, mode + ".json");
                    File.WriteAllText(request, mode);
                    var exit = Launch(supervisor, worker, request, resultPath);
                    var expected = mode == "stage-timeout" ? 124 : 3;
                    if (exit != expected) throw new Exception("Wrong failure exit");
                    var result = new JavaScriptSerializer().Deserialize<Dictionary<string, object>>(File.ReadAllText(resultPath));
                    var eventsPath = resultPath + ".events.jsonl";
                    if (!File.Exists(eventsPath)) throw new Exception(mode + " stage evidence was deleted");
                    if (!result.ContainsKey("StageEventsPath") || (string)result["StageEventsPath"] != eventsPath)
                        throw new Exception("Result does not locate retained evidence");
                    if ((bool)result["Success"]) throw new Exception("Partial events promoted failure to success");
                    if (mode == "stage-timeout" && ((string)result["Cleanup"] != "UNKNOWN" || result["Observation"] != null))
                        throw new Exception("Timeout fabricated cleanup or observation");
                    var marker = mode == "stage-timeout" ? "before:Initialize" : "before:Load";
                    if (!File.ReadAllText(eventsPath).Contains(marker)) throw new Exception("Worker marker lost");
                    Console.WriteLine("PASS retained " + mode);
                }
                var blocked = Path.Combine(root, "blocked.request");
                var output = Path.Combine(root, "blocked.json");
                File.WriteAllText(blocked, "normal");
                File.WriteAllText(output + ".events.jsonl", "previous evidence");
                if (Launch(supervisor, worker, blocked, output) != 64 || File.Exists(blocked + ".started"))
                    throw new Exception("Existing events did not prevent worker launch");
                if (File.ReadAllText(output + ".events.jsonl") != "previous evidence")
                    throw new Exception("Prior events overwritten");
                Console.WriteLine("PASS existing events block launch");
                var relativeRequest = Path.Combine(root, "root-relative.request");
                var relativeOutput = Path.Combine(root, "root-relative.json");
                File.WriteAllText(relativeRequest, "normal");
                if (Path.GetPathRoot(relativeOutput).Length == 3)
                {
                    if (Launch(supervisor, worker, relativeRequest, relativeOutput.Substring(2)) != 64 ||
                        File.Exists(relativeRequest + ".started"))
                        throw new Exception("Root-relative evidence path launched worker");
                    Console.WriteLine("PASS root-relative events rejected");
                }
            }
            finally { try { Directory.Delete(root, true); } catch { } }
        }

        private static int Launch(string supervisor, string worker, string request, string result)
        {
            using (var process = Process.Start(new ProcessStartInfo {
                FileName = supervisor,
                Arguments = "--worker \"" + worker + "\" --request \"" + request + "\" --timeout-seconds 2 --result-file \"" + result + "\"",
                UseShellExecute = true, WindowStyle = ProcessWindowStyle.Hidden }))
            {
                if (!process.WaitForExit(4500)) throw new Exception("Capture deadline exceeded");
                return process.ExitCode;
            }
        }
    }
}
