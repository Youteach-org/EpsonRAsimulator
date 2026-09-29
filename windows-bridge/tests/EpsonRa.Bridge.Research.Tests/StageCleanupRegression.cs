using System;
using System.Collections.Generic;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Worker;

namespace EpsonRa.Bridge.Research.Tests
{
    // Standalone Roslyn runner and MSTest share these real runner regressions.
    public static class StageCleanupRegression
    {
        public static int Main()
        {
            var failures = 0;
            foreach (var scenario in new[] { "Verify", "Disconnect", "InvalidPrior", "DuplicateName", "EventFailure" })
            {
                try { Check(scenario); Console.WriteLine("PASS " + scenario); }
                catch (Exception e) { failures++; Console.WriteLine("FAIL " + scenario + ": " + e.Message); }
            }
            return failures == 0 ? 0 : 1;
        }

        public static void Check(string scenario)
        {
            var api = new FaultApi { Fault = scenario };
            var request = new NativeStageRequest { Stage = Stage.Connect, Approved = true,
                Target = "C4 Sample", ServerInstance = 1, PriorEligibleName = "C4 Sample",
                PriorEligibleType = "Virtual", PriorEligibleOrdinal = 3 };
            if (scenario == "InvalidPrior") request.PriorEligibleName = null;
            if (scenario == "DuplicateName") request.Stage = Stage.Inventory;
            var result = NativeStageRunner.Run(request, api, marker => {
                if (scenario == "EventFailure" && marker.Name == "before:Disconnect") throw new InvalidOperationException();
            });
            Require(!result.Success, "uncertainty must fail closed");
            if (scenario == "InvalidPrior") Require(api.Calls.Count == 0, "invalid prior evidence must reject before native activation");
            else {
                Require(api.Calls.FindAll(x => x == "Dispose").Count == 1, "dispose exactly once");
                if (scenario != "DuplicateName") Require(api.Calls.FindAll(x => x == "Disconnect").Count == 1, "disconnect once after successful connect, even if verification/event writing fails");
            }
        }

        private static void Require(bool value, string message) { if (!value) throw new Exception(message); }
        private sealed class FaultApi : INativeApi
        {
            public string Fault;
            public List<string> Calls = new List<string>();
            public void Load(string root) { Calls.Add("Load"); }
            public void Construct() { Calls.Add("Construct"); }
            public void SetServerInstance(int value) { Calls.Add("SetServerInstance"); }
            public void Initialize() { Calls.Add("Initialize"); }
            public IReadOnlyList<NativeConnection> GetConnections() { return new[] {
                new NativeConnection { Name = "C4 Sample", Type = "Virtual", Ordinal = 3 },
                new NativeConnection { Name = "C4 Sample", Type = "USB", Ordinal = 1 } }; }
            public void ConnectByName(string name) { Calls.Add("Connect"); }
            public NativeConnection GetCurrentConnection() {
                if (Fault == "Verify") throw new InvalidOperationException();
                return new NativeConnection { Name = "C4 Sample", Type = "Virtual", Ordinal = 3 };
            }
            public void Disconnect() { Calls.Add("Disconnect"); if (Fault == "Disconnect") throw new InvalidOperationException(); }
            public void Dispose() { Calls.Add("Dispose"); }
        }
    }
}
