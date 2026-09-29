using System;
using System.Collections.Generic;
using System.Linq;
using EpsonRa.Bridge.Research;

namespace EpsonRa.Bridge.Research.Worker
{
    public sealed class NativeConnection
    {
        public string Name { get; set; }
        public string Type { get; set; }
        public int Ordinal { get; set; }
    }

    public sealed class NativeStageRequest
    {
        public Stage Stage { get; set; }
        public string InstallRoot { get; set; }
        public string Target { get; set; }
        public int? ServerInstance { get; set; }
        public bool Approved { get; set; }
        public string PriorEligibleName { get; set; }
        public string PriorEligibleType { get; set; }
        public int? PriorEligibleOrdinal { get; set; }
    }

    public sealed class NativeStageResult
    {
        public int SchemaVersion { get; set; }
        public string Status { get; set; }
        public bool Success { get; set; }
        public string Cleanup { get; set; }
        public string Error { get; set; }
        public int? EligibleOrdinal { get; set; }
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
        public static NativeStageResult Run(NativeStageRequest request, INativeApi api, Action<string> eventSink)
        {
            if (request == null || api == null)
                return Failed("InvalidArguments");

            var validation = StagePolicy.Validate(request.Stage, request.Target, request.ServerInstance, request.Approved);
            if (!validation.IsValid)
                return Failed("StagePolicyRejected");

            if (request.Stage == Stage.MetadataOnly)
                return Completed();

            try
            {
                Mark(eventSink, "before:Load");
                api.Load(request.InstallRoot);
                Mark(eventSink, "after:Load");

                if (request.Stage == Stage.LoadOnly)
                    return Completed();

                Mark(eventSink, "before:Construct");
                api.Construct();
                Mark(eventSink, "after:Construct");

                Mark(eventSink, "before:SetServerInstance");
                api.SetServerInstance(request.ServerInstance.Value);
                Mark(eventSink, "after:SetServerInstance");

                Mark(eventSink, "before:Initialize");
                api.Initialize();
                Mark(eventSink, "after:Initialize");

                if (request.Stage == Stage.InitializeObserve)
                    return CompletedWithDispose(api, eventSink);

                if (request.Stage == Stage.Inventory)
                {
                    var eligible = api.GetConnections()
                        .Where(x => x != null && x.Name == "C4 Sample" && x.Type == "Virtual" && x.Ordinal == 3)
                        .ToList();
                    if (eligible.Count != 1)
                        return FailedWithDispose(api, eventSink, "InventoryEligibility");
                    var result = CompletedWithDispose(api, eventSink);
                    result.EligibleName = eligible[0].Name;
                    result.EligibleOrdinal = eligible[0].Ordinal;
                    return result;
                }

                if (request.Stage == Stage.Connect)
                {
                    if (request.PriorEligibleName != "C4 Sample" ||
                        request.PriorEligibleType != "Virtual" ||
                        request.PriorEligibleOrdinal != 3)
                        return FailedWithDispose(api, eventSink, "PriorEligibilityRequired");

                    api.ConnectByName("C4 Sample");
                    var current = api.GetCurrentConnection();
                    if (current == null || current.Name != "C4 Sample" || current.Type != "Virtual" || current.Ordinal != 3)
                        return FailedWithCleanup(api, eventSink, "ConnectedIdentityMismatch");

                    Mark(eventSink, "before:Disconnect");
                    api.Disconnect();
                    Mark(eventSink, "after:Disconnect");
                    return CompletedWithDispose(api, eventSink);
                }

                return FailedWithDispose(api, eventSink, "UnsupportedStage");
            }
            catch
            {
                return FailedWithDispose(api, eventSink, "NativeStageException");
            }
        }

        private static NativeStageResult CompletedWithDispose(INativeApi api, Action<string> sink)
        {
            try
            {
                Mark(sink, "before:Dispose");
                api.Dispose();
                Mark(sink, "after:Dispose");
                return Completed();
            }
            catch { return Failed("DisposeFailure"); }
        }

        private static NativeStageResult FailedWithDispose(INativeApi api, Action<string> sink, string error)
        {
            try
            {
                Mark(sink, "before:Dispose");
                api.Dispose();
                Mark(sink, "after:Dispose");
            }
            catch { return Failed("DisposeFailure"); }
            return Failed(error);
        }

        private static NativeStageResult FailedWithCleanup(INativeApi api, Action<string> sink, string error)
        {
            try
            {
                Mark(sink, "before:Disconnect");
                api.Disconnect();
                Mark(sink, "after:Disconnect");
            }
            catch { }
            return FailedWithDispose(api, sink, error);
        }

        private static void Mark(Action<string> sink, string value)
        {
            if (sink != null) sink(value);
        }

        private static NativeStageResult Completed()
        {
            return new NativeStageResult { SchemaVersion = 1, Status = "COMPLETED", Success = true, Cleanup = "CONFIRMED" };
        }

        private static NativeStageResult Failed(string error)
        {
            return new NativeStageResult { SchemaVersion = 1, Status = "FAILED", Success = false, Cleanup = "UNKNOWN", Error = error };
        }
    }
}
