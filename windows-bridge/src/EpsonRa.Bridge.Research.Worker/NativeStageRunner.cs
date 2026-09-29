using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using EpsonRa.Bridge.Research;

namespace EpsonRa.Bridge.Research.Worker
{
    public sealed class NativeConnection
    {
        public string Name { get; set; }
        public int ConnectionNumber { get; set; }
        public int TypeNumber { get; set; }
        public string TypeName { get; set; }
    }

    public sealed class NativeStageRequest
    {
        public Stage Stage { get; set; }
        public string InstallRoot { get; set; }
        public string Target { get; set; }
        public int? ServerInstance { get; set; }
        public bool Approved { get; set; }
        public string PriorEligibleName { get; set; }
        public int? PriorEligibleTypeNumber { get; set; }
    }

    public sealed class NativeStageResult
    {
        public int SchemaVersion { get; set; }
        public string Status { get; set; }
        public bool Success { get; set; }
        public string Cleanup { get; set; }
        public string Error { get; set; }
        public int? Machine { get; set; }
        public long? CorFlags { get; set; }
        public string Architecture { get; set; }
        public int? EligibleConnectionNumber { get; set; }
        public int? EligibleTypeNumber { get; set; }
        public string EligibleName { get; set; }
    }

    public interface INativeApi : IDisposable
    {
        void Load(string installRoot);
        void Construct();
        void SetServerInstance(int instance);
        void Initialize();
        IReadOnlyList<NativeConnection> GetConnections();
        void ConnectByName(string name);
        NativeConnection GetCurrentConnection();
        void Disconnect();
    }

    public static class NativeStageRunner
    {
        private const int VirtualTypeNumber = 3;

        public static NativeStageResult Run(NativeStageRequest request, INativeApi api, Action<StageEvent> eventSink)
        {
            if (request == null || api == null)
                return Failed("InvalidArguments");

            var validation = StagePolicy.Validate(request.Stage, request.Target, request.ServerInstance, request.Approved);
            if (validation != StageValidation.Valid)
                return Failed("StagePolicyRejected");

            if (request.Stage == Stage.Connect &&
                (request.PriorEligibleName != "C4 Sample" ||
                 request.PriorEligibleTypeNumber != VirtualTypeNumber))
                return Failed("PriorEligibilityRequired");

            if (request.Stage == Stage.MetadataOnly)
                return Completed();

            var result = Completed();
            var constructionAttempted = false;
            var connected = false;
            try
            {
                Mark(eventSink, "before:Load");
                api.Load(request.InstallRoot);
                Mark(eventSink, "after:Load");

                if (request.Stage != Stage.LoadOnly)
                {
                    Mark(eventSink, "before:Construct");
                    constructionAttempted = true;
                    api.Construct();
                    Mark(eventSink, "after:Construct");

                    Mark(eventSink, "before:SetServerInstance");
                    api.SetServerInstance(request.ServerInstance.Value);
                    Mark(eventSink, "after:SetServerInstance");

                    Mark(eventSink, "before:Initialize");
                    api.Initialize();
                    Mark(eventSink, "after:Initialize");

                    if (request.Stage == Stage.Inventory)
                    {
                        var matches = api.GetConnections()
                            .Where(x => x != null && x.Name == "C4 Sample")
                            .ToList();

                        if (matches.Count != 1 || matches[0].TypeNumber != VirtualTypeNumber)
                        {
                            result = Failed("InventoryEligibility");
                        }
                        else
                        {
                            result.EligibleName = matches[0].Name;
                            result.EligibleConnectionNumber = matches[0].ConnectionNumber;
                            result.EligibleTypeNumber = matches[0].TypeNumber;
                        }
                    }
                    else if (request.Stage == Stage.Connect)
                    {
                        api.ConnectByName("C4 Sample");
                        connected = true;

                        var current = api.GetCurrentConnection();
                        if (current == null ||
                            current.Name != "C4 Sample" ||
                            current.TypeNumber != VirtualTypeNumber)
                            result = Failed("ConnectedIdentityMismatch");
                    }
                }
            }
            catch
            {
                result = Failed("NativeStageException");
            }
            finally
            {
                var cleanupOk = true;

                if (connected)
                    cleanupOk = Cleanup(api.Disconnect, "Disconnect", eventSink) && cleanupOk;

                if (constructionAttempted)
                    cleanupOk = Cleanup(api.Dispose, "Dispose", eventSink) && cleanupOk;

                if (!cleanupOk)
                {
                    result.Success = false;
                    result.Status = "FAILED";
                    result.Cleanup = "UNKNOWN";
                    result.Error = result.Error ?? "CleanupFailure";
                }
                else if (constructionAttempted)
                {
                    result.Cleanup = "CONFIRMED";
                }
            }

            return result;
        }

        private static bool Cleanup(Action action, string name, Action<StageEvent> sink)
        {
            var ok = true;
            try { Mark(sink, "before:" + name); } catch { ok = false; }
            try { action(); } catch { ok = false; }
            try { Mark(sink, "after:" + name); } catch { ok = false; }
            return ok;
        }

        private static void Mark(Action<StageEvent> sink, string value)
        {
            if (sink != null)
            {
                sink(new StageEvent
                {
                    Name = value,
                    MonotonicTicks = Stopwatch.GetTimestamp()
                });
            }
        }

        private static NativeStageResult Completed()
        {
            return new NativeStageResult
            {
                SchemaVersion = 1,
                Status = "COMPLETED",
                Success = true,
                Cleanup = "CONFIRMED"
            };
        }

        private static NativeStageResult Failed(string error)
        {
            return new NativeStageResult
            {
                SchemaVersion = 1,
                Status = "FAILED",
                Success = false,
                Cleanup = "UNKNOWN",
                Error = error
            };
        }
    }
}
