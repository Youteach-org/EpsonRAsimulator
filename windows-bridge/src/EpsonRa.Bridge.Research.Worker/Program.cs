using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Research;

namespace EpsonRa.Bridge.Research.Worker
{
    internal static class Program
    {
        private const int RequestCap = 64 * 1024;

        private static int Main(string[] args)
        {
            string requestPath;
            string eventsPath;

            if (!TryParseArguments(args, out requestPath, out eventsPath))
                return WriteInvalid();

            NativeStageRequest request;
            if (!TryReadRequest(requestPath, out request))
                return WriteInvalid();

            try
            {
                if (File.Exists(eventsPath))
                    return WriteInvalid();

                var serializer = new JavaScriptSerializer { MaxJsonLength = RequestCap };
                using (var stream = new FileStream(eventsPath, FileMode.CreateNew, FileAccess.Write, FileShare.Read))
                using (var writer = new StreamWriter(stream, new UTF8Encoding(false)) { AutoFlush = true })
                {
                    Action<StageEvent> sink = stageEvent =>
                    {
                        var wire = new Dictionary<string, object>
                        {
                            { "name", stageEvent.Name },
                            { "monotonicTicks", stageEvent.MonotonicTicks }
                        };
                        writer.WriteLine(serializer.Serialize(wire));
                    };

                    var adapter = new InstalledApiAdapter();
                    var result = NativeStageRunner.Run(request, adapter, sink);
                    WriteResult(result);
                    return result.Success ? 0 : 3;
                }
            }
            catch
            {
                WriteFailure("WorkerFailure");
                return 3;
            }
        }

        private static bool TryParseArguments(string[] args, out string requestPath, out string eventsPath)
        {
            requestPath = null;
            eventsPath = null;

            if (args == null || args.Length != 4)
                return false;

            for (var i = 0; i < args.Length; i += 2)
            {
                var name = args[i];
                var value = args[i + 1];

                if (string.IsNullOrWhiteSpace(value))
                    return false;

                if (name == "--request" && requestPath == null)
                    requestPath = value;
                else if (name == "--events" && eventsPath == null)
                    eventsPath = value;
                else
                    return false;
            }

            if (requestPath == null || eventsPath == null ||
                !Path.IsPathRooted(requestPath) ||
                !Path.IsPathRooted(eventsPath) ||
                !File.Exists(requestPath))
                return false;

            return true;
        }

        private static bool TryReadRequest(string path, out NativeStageRequest request)
        {
            request = null;

            try
            {
                var info = new FileInfo(path);
                if (!info.Exists || info.Length <= 0 || info.Length > RequestCap)
                    return false;

                var text = File.ReadAllText(path, Encoding.UTF8);
                var serializer = new JavaScriptSerializer { MaxJsonLength = RequestCap };
                var values = serializer.DeserializeObject(text) as Dictionary<string, object>;
                if (values == null)
                    return false;

                var allowed = new HashSet<string>(StringComparer.Ordinal)
                {
                    "stage",
                    "installRoot",
                    "target",
                    "serverInstance",
                    "approved",
                    "priorEligibleName",
                    "priorEligibleTypeNumber"
                };

                foreach (var key in values.Keys)
                    if (!allowed.Contains(key))
                        return false;

                object rawStage;
                object rawRoot;
                object rawApproved;
                if (!values.TryGetValue("stage", out rawStage) || !(rawStage is string) ||
                    !values.TryGetValue("installRoot", out rawRoot) || !(rawRoot is string) ||
                    !values.TryGetValue("approved", out rawApproved) || !(rawApproved is bool))
                    return false;

                Stage stage;
                if (!Enum.TryParse((string)rawStage, false, out stage) ||
                    !Enum.IsDefined(typeof(Stage), stage))
                    return false;

                var root = (string)rawRoot;
                if (string.IsNullOrWhiteSpace(root) || !Path.IsPathRooted(root))
                    return false;

                string target;
                if (!TryOptionalString(values, "target", out target))
                    return false;

                int? serverInstance;
                if (!TryOptionalInt(values, "serverInstance", out serverInstance))
                    return false;

                string priorName;
                if (!TryOptionalString(values, "priorEligibleName", out priorName))
                    return false;

                int? priorTypeNumber;
                if (!TryOptionalInt(values, "priorEligibleTypeNumber", out priorTypeNumber))
                    return false;

                request = new NativeStageRequest
                {
                    Stage = stage,
                    InstallRoot = Path.GetFullPath(root),
                    Target = target,
                    ServerInstance = serverInstance,
                    Approved = (bool)rawApproved,
                    PriorEligibleName = priorName,
                    PriorEligibleTypeNumber = priorTypeNumber
                };

                return true;
            }
            catch
            {
                request = null;
                return false;
            }
        }

        private static bool TryOptionalString(
            Dictionary<string, object> values,
            string key,
            out string value)
        {
            value = null;
            object raw;
            if (!values.TryGetValue(key, out raw) || raw == null)
                return true;
            if (!(raw is string))
                return false;
            value = (string)raw;
            return true;
        }

        private static bool TryOptionalInt(
            Dictionary<string, object> values,
            string key,
            out int? value)
        {
            value = null;
            object raw;
            if (!values.TryGetValue(key, out raw) || raw == null)
                return true;
            if (!(raw is int))
                return false;
            value = (int)raw;
            return true;
        }

        private static int WriteInvalid()
        {
            WriteWire("INVALID_ARGUMENTS", false, "UNKNOWN", "InvalidArguments", null);
            return 64;
        }

        private static void WriteFailure(string error)
        {
            WriteWire("FAILED", false, "UNKNOWN", error, null);
        }

        private static void WriteResult(NativeStageResult result)
        {
            WriteWire(result.Status, result.Success, result.Cleanup, result.Error, result);
        }

        private static void WriteWire(
            string status,
            bool success,
            string cleanup,
            string error,
            NativeStageResult result)
        {
            var wire = new Dictionary<string, object>
            {
                { "schemaVersion", 1 },
                { "status", status },
                { "success", success },
                { "cleanup", cleanup },
                { "error", error }
            };

            if (result != null)
            {
                wire["eligibleName"] = result.EligibleName;
                wire["eligibleConnectionNumber"] = result.EligibleConnectionNumber;
                wire["eligibleTypeNumber"] = result.EligibleTypeNumber;
            }

            Console.Out.WriteLine(new JavaScriptSerializer().Serialize(wire));
        }
    }
}
