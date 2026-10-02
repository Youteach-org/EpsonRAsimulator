using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using EpsonRa.Bridge.Research;
using EpsonRa.Bridge.Research.Supervisor;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class ObservationDeadlineRegression
    {
        public static int Main(string[] args)
        {
            if (args.Length != 1) return 64;
            var failures = 0;
            foreach (var point in new[] { "Create", "Started", "Poll", "Dispose" })
            {
                try { Check(args[0], point); Console.WriteLine("PASS " + point); }
                catch (Exception e) { failures++; Console.WriteLine("FAIL " + point + ": " + e.Message); }
            }
            return failures == 0 ? 0 : 1;
        }
        public static void Check(string fixture, string point)
        {
            var path = Path.GetTempFileName();
            try {
                File.WriteAllText(path, "{\"stage\":\"InitializeObserve\",\"installRoot\":\"C:\\\\SyntheticEpson\",\"target\":\"C4 Sample\",\"serverInstance\":1,\"approved\":true}");
                var clock = Stopwatch.StartNew();
                var result = WorkerSupervisor.Run(new WorkerRequest { WorkerPath = fixture,
                    RequestPath = path, TimeoutSeconds = 1, ExtraArguments = new[] { "observed" } }, new SlowMonitor(point));
                if (clock.Elapsed.TotalSeconds > 3.5) throw new Exception("observer exceeded deadline and termination allowance");
                if (result.Success || result.ExitCode != 124) throw new Exception("stalled observer must time out, never succeed");
            } finally { File.Delete(path); }
        }
        private sealed class SlowMonitor : IObservationMonitorFactory, IObservationMonitor
        {
            private readonly string point;
            public SlowMonitor(string point) { this.point = point; }
            private void Hit(string name) { if (point == name) Thread.Sleep(4500); }
            public IObservationMonitor Create(string root) { Hit("Create"); return this; }
            public ObservationSnapshot Before { get { return Snapshot(0); } }
            public ObservationSnapshot After { get { return Snapshot(20); } }
            private ObservationSnapshot Snapshot(long ticks) { return new ObservationSnapshot {
                MonotonicTicks = ticks, ProcessSampleAvailable = true, TcpSampleAvailable = true,
                TcpIpv6SampleAvailable = true, OwnershipUnambiguous = true }; }
            public void WorkerStarted(int id) { Hit("Started"); }
            public void Poll() { Hit("Poll"); }
            public void Dispose() { Hit("Dispose"); }
        }
    }
}

