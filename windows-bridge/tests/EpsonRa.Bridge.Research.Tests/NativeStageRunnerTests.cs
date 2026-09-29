using System;
using System.Collections.Generic;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Worker;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class NativeStageRunnerTests
    {
        [TestMethod]
        public void MetadataOnlyMakesZeroNativeCalls()
        {
            var api = new FakeApi();
            var result = NativeStageRunner.Run(Request(Stage.MetadataOnly, false), api, _ => { });
            CollectionAssert.AreEqual(new string[0], api.Calls);
            Assert.IsTrue(result.Success);
        }

        [TestMethod]
        public void LoadOnlyCallsLoadOnly()
        {
            var api = new FakeApi();
            NativeStageRunner.Run(Request(Stage.LoadOnly, true), api, _ => { });
            CollectionAssert.AreEqual(new[] { "Load" }, api.Calls);
        }

        [TestMethod]
        public void InitializeObserveHasStrictCallBoundary()
        {
            var api = new FakeApi();
            NativeStageRunner.Run(Request(Stage.InitializeObserve, true), api, _ => { });
            CollectionAssert.AreEqual(new[] { "Load", "Construct", "SetServerInstance:1", "Initialize", "Dispose" }, api.Calls);
            Assert.IsFalse(Array.Exists(api.Calls, x => x.StartsWith("GetConnections")));
            Assert.IsFalse(Array.Exists(api.Calls, x => x.StartsWith("GetCurrentConnection")));
            Assert.IsFalse(Array.Exists(api.Calls, x => x.StartsWith("Connect")));
        }

        [TestMethod]
        public void InventorySelectsOnlyUniqueVirtual3Ordinal()
        {
            var api = new FakeApi();
            api.Connections.Add(new NativeConnection { Name = "Other", Type = "Virtual", Ordinal = 2 });
            api.Connections.Add(new NativeConnection { Name = "C4 Sample", Type = "Virtual", Ordinal = 3 });
            var result = NativeStageRunner.Run(Request(Stage.Inventory, true), api, _ => { });
            Assert.IsTrue(result.Success);
            Assert.AreEqual(3, result.EligibleOrdinal);
            Assert.AreEqual("C4 Sample", result.EligibleName);
        }

        [TestMethod]
        public void InventoryRejectsDuplicateEligibleConnection()
        {
            var api = new FakeApi();
            api.Connections.Add(new NativeConnection { Name = "C4 Sample", Type = "Virtual", Ordinal = 3 });
            api.Connections.Add(new NativeConnection { Name = "C4 Sample", Type = "Virtual", Ordinal = 3 });
            Assert.IsFalse(NativeStageRunner.Run(Request(Stage.Inventory, true), api, _ => { }).Success);
        }

        [TestMethod]
        public void ConnectRequiresPriorEligibilityAndExactIdentity()
        {
            var api = new FakeApi();
            var request = Request(Stage.Connect, true);
            request.PriorEligibleName = "C4 Sample";
            request.PriorEligibleType = "Virtual";
            request.PriorEligibleOrdinal = 3;
            NativeStageRunner.Run(request, api, _ => { });
            CollectionAssert.Contains(api.Calls, "ConnectByName:C4 Sample");
            Assert.IsFalse(Array.Exists(api.Calls, x => x == "ConnectByOrdinal:3"));
        }

        [TestMethod]
        public void ConnectRejectsMissingOrMismatchedPriorEligibility()
        {
            foreach (var name in new[] { null, "c4 sample", " C4 Sample " })
            {
                var api = new FakeApi();
                var request = Request(Stage.Connect, true);
                request.PriorEligibleName = name;
                request.PriorEligibleType = "Virtual";
                request.PriorEligibleOrdinal = 3;
                Assert.IsFalse(NativeStageRunner.Run(request, api, _ => { }).Success);
                Assert.IsFalse(Array.Exists(api.Calls, x => x.StartsWith("Connect")));
            }
        }

        [TestMethod]
        public void InitializeExceptionsFailClosedAndStillDispose()
        {
            var api = new FakeApi { ThrowOn = "Initialize" };
            var result = NativeStageRunner.Run(Request(Stage.InitializeObserve, true), api, _ => { });
            Assert.IsFalse(result.Success);
            CollectionAssert.Contains(api.Calls, "Dispose");
        }


        [TestMethod]
        public void InitializeObserveEmitsMonotonicBoundaryEvents()
        {
            var api = new FakeApi();
            var events = new List<StageEvent>();

            NativeStageRunner.Run(Request(Stage.InitializeObserve, true), api, events.Add);

            CollectionAssert.AreEqual(
                new[]
                {
                    "before:Load", "after:Load",
                    "before:Construct", "after:Construct",
                    "before:SetServerInstance", "after:SetServerInstance",
                    "before:Initialize", "after:Initialize",
                    "before:Dispose", "after:Dispose"
                },
                events.ConvertAll(x => x.Name).ToArray());

            for (var i = 1; i < events.Count; i++)
                Assert.IsTrue(events[i].MonotonicTicks >= events[i - 1].MonotonicTicks);
        }

        [TestMethod]
        public void FailuresAfterInstanceExistsAttemptDisposeWithoutRetry()
        {
            foreach (var failure in new[] { "SetServerInstance:1", "Initialize", "GetConnections" })
            {
                var api = new FakeApi { ThrowOn = failure };
                var stage = failure == "GetConnections" ? Stage.Inventory : Stage.InitializeObserve;

                var result = NativeStageRunner.Run(Request(stage, true), api, _ => { });

                Assert.IsFalse(result.Success, failure);
                CollectionAssert.Contains(api.Calls, "Dispose", failure);
                Assert.AreEqual(1, Array.FindAll(api.Calls, x => x == failure).Length, failure);
            }
        }

        [TestMethod]
        public void ConnectVerificationExceptionStillAttemptsDisconnectAndDispose()
        {
            var api = new FakeApi { ThrowOn = "GetCurrentConnection" };
            var request = EligibleConnectRequest();

            var result = NativeStageRunner.Run(request, api, _ => { });

            Assert.IsFalse(result.Success);
            CollectionAssert.Contains(api.Calls, "ConnectByName:C4 Sample");
            CollectionAssert.Contains(api.Calls, "Disconnect");
            CollectionAssert.Contains(api.Calls, "Dispose");
        }

        [TestMethod]
        public void ConnectedIdentityMismatchAttemptsDisconnectAndDispose()
        {
            var api = new FakeApi
            {
                CurrentConnection = new NativeConnection { Name = "Other", Type = "Virtual", Ordinal = 3 }
            };
            var result = NativeStageRunner.Run(EligibleConnectRequest(), api, _ => { });

            Assert.IsFalse(result.Success);
            CollectionAssert.Contains(api.Calls, "Disconnect");
            CollectionAssert.Contains(api.Calls, "Dispose");
        }

        [TestMethod]
        public void DisconnectOrDisposeFailureNeverBecomesSuccess()
        {
            foreach (var failure in new[] { "Disconnect", "Dispose" })
            {
                var api = new FakeApi { ThrowOn = failure };
                var result = NativeStageRunner.Run(EligibleConnectRequest(), api, _ => { });

                Assert.IsFalse(result.Success, failure);
                Assert.AreNotEqual("CONFIRMED", result.Cleanup, failure);
            }
        }

        private static NativeStageRequest EligibleConnectRequest()
        {
            var request = Request(Stage.Connect, true);
            request.PriorEligibleName = "C4 Sample";
            request.PriorEligibleType = "Virtual";
            request.PriorEligibleOrdinal = 3;
            return request;
        }

        private static NativeStageRequest Request(Stage stage, bool approved)
        {
            return new NativeStageRequest
            {
                Stage = stage,
                InstallRoot = @"C:\EpsonRC70",
                Target = stage == Stage.MetadataOnly || stage == Stage.LoadOnly ? null : "C4 Sample",
                ServerInstance = stage == Stage.MetadataOnly || stage == Stage.LoadOnly ? (int?)null : 1,
                Approved = approved
            };
        }

        private sealed class FakeApi : INativeApi
        {
            public readonly List<NativeConnection> Connections = new List<NativeConnection>();
            public List<string> CallList = new List<string>();
            public string[] Calls { get { return CallList.ToArray(); } }
            public string ThrowOn { get; set; }
            public NativeConnection CurrentConnection { get; set; }

            private void Hit(string value)
            {
                CallList.Add(value);
                if (ThrowOn == value) throw new InvalidOperationException(value);
            }

            public void Load(string installRoot) { Hit("Load"); }
            public void Construct() { Hit("Construct"); }
            public void SetServerInstance(int instance) { Hit("SetServerInstance:" + instance); }
            public void Initialize() { Hit("Initialize"); }
            public IReadOnlyList<NativeConnection> GetConnections() { Hit("GetConnections"); return Connections; }
            public void ConnectByName(string name) { Hit("ConnectByName:" + name); }
            public NativeConnection GetCurrentConnection()
            {
                Hit("GetCurrentConnection");
                return CurrentConnection ?? new NativeConnection { Name="C4 Sample", Type="Virtual", Ordinal=3 };
            }
            public void Disconnect() { Hit("Disconnect"); }
            public void Dispose() { Hit("Dispose"); }
        }
    }
}
