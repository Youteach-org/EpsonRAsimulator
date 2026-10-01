using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Web.Script.Serialization;
using EpsonRa.Bridge.Research;

namespace EpsonRa.Bridge.Research.Supervisor
{
    public interface IObservationMonitorFactory
    {
        IObservationMonitor Create(string installRoot);
    }

    public interface IObservationMonitor : IDisposable
    {
        ObservationSnapshot Before { get; }
        ObservationSnapshot After { get; }
        void WorkerStarted(int processId);
        void Poll();
    }

    public sealed class SystemObservationMonitorFactory : IObservationMonitorFactory
    {
        public IObservationMonitor Create(string installRoot)
        {
            return new SystemObservationMonitor(installRoot);
        }
    }

    public static class ExternalObservation
    {
        public static ObservationAssessment Evaluate(
            ObservationSnapshot before,
            ObservationSnapshot after,
            string eventsPath,
            Stage stage)
        {
            var assessment = ObservationEvaluator.Compare(before, after);
            var events = ReadEvents(eventsPath);
            var expected = Expected(stage);
            var complete = events != null && expected != null && events.Count == expected.Length;

            if (complete)
            {
                long previous = long.MinValue;
                for (var i = 0; i < expected.Length; i++)
                {
                    if (events[i].Name != expected[i] || events[i].MonotonicTicks < previous ||
                        before == null || after == null || events[i].MonotonicTicks < before.MonotonicTicks ||
                        events[i].MonotonicTicks > after.MonotonicTicks)
                    {
                        complete = false;
                        break;
                    }
                    previous = events[i].MonotonicTicks;
                }
            }

            assessment.EventCount = events == null ? 0 : events.Count;
            assessment.StageEvents = events == null ? new string[0] : events.ConvertAll(x => x.Name).ToArray();
            assessment.EventTraceComplete = complete;
            assessment.Conclusive = assessment.Conclusive && complete;
            assessment.Status = assessment.Conclusive ? "OBSERVED" : "INCONCLUSIVE";
            return assessment;
        }

        private static List<StageEvent> ReadEvents(string path)
        {
            try
            {
                if (string.IsNullOrWhiteSpace(path) || !File.Exists(path))
                    return null;

                var info = new FileInfo(path);
                if (info.Length <= 0 || info.Length > 64 * 1024)
                    return null;

                var serializer = new JavaScriptSerializer();
                var result = new List<StageEvent>();
                using (var reader = new StreamReader(path))
                {
                    string line;
                    while ((line = reader.ReadLine()) != null)
                    {
                        if (result.Count >= 64 || string.IsNullOrWhiteSpace(line))
                            return null;

                        var dict = serializer.DeserializeObject(line) as Dictionary<string, object>;
                    object name;
                    object ticks;
                    if (dict == null ||
                        dict.Count != 2 ||
                        !dict.TryGetValue("name", out name) || !(name is string) ||
                        !dict.TryGetValue("monotonicTicks", out ticks) ||
                        !(ticks is int) && !(ticks is long))
                        return null;

                        var eventName = (string)name;
                        if (!AllowedEvent(eventName) || Convert.ToInt64(ticks) < 0) return null;
                        result.Add(new StageEvent
                        {
                            Name = (string)name,
                            MonotonicTicks = Convert.ToInt64(ticks)
                        });
                    }
                }
                return result;
            }
            catch
            {
                return null;
            }
        }

        private static string[] Expected(Stage stage)
        {
            if (stage == Stage.LoadOnly)
            {
                return new[] { "before:Load", "after:Load" };
            }

            if (stage == Stage.InitializeObserve || stage == Stage.Inventory)
            {
                return new[]
                {
                    "before:Load", "after:Load",
                    "before:Construct", "after:Construct",
                    "before:SetServerInstance", "after:SetServerInstance",
                    "before:Initialize", "after:Initialize",
                    "before:Dispose", "after:Dispose"
                };
            }

            if (stage == Stage.Connect)
            {
                return new[]
                {
                    "before:Load", "after:Load",
                    "before:Construct", "after:Construct",
                    "before:SetServerInstance", "after:SetServerInstance",
                    "before:Initialize", "after:Initialize",
                    "before:Disconnect", "after:Disconnect",
                    "before:Dispose", "after:Dispose"
                };
            }

            return Array.Empty<string>();
        }

        private static bool AllowedEvent(string name)
        {
            foreach (var operation in new[] { "Load", "Construct", "SetServerInstance", "Initialize", "Disconnect", "Dispose" })
                if (name == "before:" + operation || name == "after:" + operation) return true;
            return false;
        }
    }

    internal sealed class SystemObservationMonitor : IObservationMonitor
    {
        private readonly string _installPrefix;
        private int _workerPid;
        private bool _workerObserved;
        private bool _disposed;
        private ObservationSnapshot _after;

        public SystemObservationMonitor(string installRoot)
        {
            var full = Path.GetFullPath(installRoot ?? string.Empty);
            _installPrefix = full.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
                + Path.DirectorySeparatorChar;
            Before = Capture();
            _after = Before;
        }

        public ObservationSnapshot Before { get; private set; }

        public ObservationSnapshot After
        {
            get { return _after; }
        }

        public void WorkerStarted(int processId)
        {
            _workerPid = processId;
            _workerObserved = ProcessExists(processId);
            Poll();
        }

        public void Poll()
        {
            if (_disposed)
                return;

            var sample = Capture();
            _after = MergeMax(_after, sample);
        }

        public void Dispose()
        {
            if (_disposed)
                return;
            try { Poll(); } catch { }
            _disposed = true;
        }

        private ObservationSnapshot Capture()
        {
            var snapshot = new ObservationSnapshot
            {
                MonotonicTicks = Stopwatch.GetTimestamp(),
                ProcessSampleAvailable = true,
                TcpSampleAvailable = true,
                TcpIpv6SampleAvailable = true,
                OwnershipUnambiguous = _workerPid == 0 || _workerObserved
            };

            var observedPids = new HashSet<int>();
            try
            {
                foreach (var process in Process.GetProcesses())
                {
                    using (process)
                    {
                        var pid = SafePid(process);
                        if (pid <= 0)
                            continue;

                        if (pid == _workerPid)
                        {
                            snapshot.OwnedProcessCount++;
                            observedPids.Add(pid);
                            continue;
                        }

                        string path;
                        if (!TryProcessPath(process, out path))
                        {
                            // PID 4 is the Windows System process and cannot be an Epson user process.
                            if (pid != 4)
                                snapshot.ProcessAccessGapCount++;
                            continue;
                        }

                        if (!string.IsNullOrEmpty(path) &&
                            path.StartsWith(_installPrefix, StringComparison.OrdinalIgnoreCase))
                        {
                            snapshot.UnrelatedProcessCount++;
                            observedPids.Add(pid);
                        }
                    }
                }
            }
            catch
            {
                snapshot.ProcessSampleAvailable = false;
                snapshot.OwnershipUnambiguous = false;
            }

            Dictionary<int, int> tcpByPid;
            bool ipv6Available;
            if (!TcpOwnerSnapshot.TryCapture(out tcpByPid, out ipv6Available))
                snapshot.TcpSampleAvailable = false;
            snapshot.TcpIpv6SampleAvailable = ipv6Available;

            foreach (var pair in tcpByPid)
            {
                if (_workerPid > 0 && pair.Key == _workerPid)
                    snapshot.OwnedTcpCount += pair.Value;
                else if (observedPids.Contains(pair.Key))
                    snapshot.UnrelatedTcpCount += pair.Value;
            }

            return snapshot;
        }

        private static ObservationSnapshot MergeMax(ObservationSnapshot current, ObservationSnapshot next)
        {
            if (current == null)
                return next;
            if (next == null)
                return current;

            return new ObservationSnapshot
            {
                MonotonicTicks = Math.Max(current.MonotonicTicks, next.MonotonicTicks),
                ProcessSampleAvailable = current.ProcessSampleAvailable && next.ProcessSampleAvailable,
                TcpSampleAvailable = current.TcpSampleAvailable && next.TcpSampleAvailable,
                TcpIpv6SampleAvailable = current.TcpIpv6SampleAvailable && next.TcpIpv6SampleAvailable,
                OwnershipUnambiguous = current.OwnershipUnambiguous && next.OwnershipUnambiguous,
                ProcessAccessGapCount = Math.Max(current.ProcessAccessGapCount, next.ProcessAccessGapCount),
                OwnedProcessCount = Math.Max(current.OwnedProcessCount, next.OwnedProcessCount),
                OwnedTcpCount = Math.Max(current.OwnedTcpCount, next.OwnedTcpCount),
                UnrelatedProcessCount = Math.Max(current.UnrelatedProcessCount, next.UnrelatedProcessCount),
                UnrelatedTcpCount = Math.Max(current.UnrelatedTcpCount, next.UnrelatedTcpCount)
            };
        }

        private static int SafePid(Process process)
        {
            try { return process.Id; }
            catch { return -1; }
        }

        private static bool ProcessExists(int pid)
        {
            try
            {
                using (var process = Process.GetProcessById(pid))
                    return !process.HasExited;
            }
            catch
            {
                return false;
            }
        }

        private static bool TryProcessPath(Process process, out string path)
        {
            path = null;
            try
            {
                path = process.MainModule == null ? null : process.MainModule.FileName;
                return true;
            }
            catch
            {
                return false;
            }
        }

        private static class TcpOwnerSnapshot
        {
            private const int AfInet = 2;
            private const int AfInet6 = 23;
            private const int ErrorInsufficientBuffer = 122;
            private const int TcpTableOwnerPidAll = 5;

            [DllImport("iphlpapi.dll", SetLastError = true)]
            private static extern uint GetExtendedTcpTable(
                IntPtr tcpTable,
                ref int size,
                bool order,
                int ipVersion,
                int tableClass,
                uint reserved);

            [StructLayout(LayoutKind.Sequential)]
            private struct MibTcpRowOwnerPid
            {
                public uint State;
                public uint LocalAddr;
                public uint LocalPort;
                public uint RemoteAddr;
                public uint RemotePort;
                public uint OwningPid;
            }

            [StructLayout(LayoutKind.Sequential)]
            private struct MibTcp6RowOwnerPid
            {
                public uint LocalAddr0;
                public uint LocalAddr1;
                public uint LocalAddr2;
                public uint LocalAddr3;
                public uint LocalScopeId;
                public uint LocalPort;
                public uint RemoteAddr0;
                public uint RemoteAddr1;
                public uint RemoteAddr2;
                public uint RemoteAddr3;
                public uint RemoteScopeId;
                public uint RemotePort;
                public uint State;
                public uint OwningPid;
            }

            public static bool TryCapture(
                out Dictionary<int, int> counts,
                out bool ipv6Available)
            {
                counts = new Dictionary<int, int>();

                Dictionary<int, int> ipv4;
                Dictionary<int, int> ipv6;
                var ipv4Available = TryCaptureIpv4(out ipv4);
                ipv6Available = TryCaptureIpv6(out ipv6);

                Merge(counts, ipv4);
                Merge(counts, ipv6);

                return ipv4Available && ipv6Available;
            }

            private static bool TryCaptureIpv4(out Dictionary<int, int> counts)
            {
                counts = new Dictionary<int, int>();
                IntPtr buffer = IntPtr.Zero;
                try
                {
                    var size = 0;
                    var first = GetExtendedTcpTable(
                        IntPtr.Zero,
                        ref size,
                        false,
                        AfInet,
                        TcpTableOwnerPidAll,
                        0);

                    if (first != ErrorInsufficientBuffer || size <= 0)
                        return false;

                    buffer = Marshal.AllocHGlobal(size);
                    if (GetExtendedTcpTable(
                        buffer,
                        ref size,
                        false,
                        AfInet,
                        TcpTableOwnerPidAll,
                        0) != 0)
                        return false;

                    var rows = Marshal.ReadInt32(buffer);
                    var rowPtr = IntPtr.Add(buffer, sizeof(int));
                    var rowSize = Marshal.SizeOf(typeof(MibTcpRowOwnerPid));

                    for (var i = 0; i < rows; i++)
                    {
                        var row = (MibTcpRowOwnerPid)Marshal.PtrToStructure(
                            IntPtr.Add(rowPtr, i * rowSize),
                            typeof(MibTcpRowOwnerPid));
                        Increment(counts, unchecked((int)row.OwningPid));
                    }

                    return true;
                }
                catch
                {
                    counts.Clear();
                    return false;
                }
                finally
                {
                    if (buffer != IntPtr.Zero)
                        Marshal.FreeHGlobal(buffer);
                }
            }

            private static bool TryCaptureIpv6(out Dictionary<int, int> counts)
            {
                counts = new Dictionary<int, int>();
                IntPtr buffer = IntPtr.Zero;
                try
                {
                    var size = 0;
                    var first = GetExtendedTcpTable(
                        IntPtr.Zero,
                        ref size,
                        false,
                        AfInet6,
                        TcpTableOwnerPidAll,
                        0);

                    if (first != ErrorInsufficientBuffer || size <= 0)
                        return false;

                    buffer = Marshal.AllocHGlobal(size);
                    if (GetExtendedTcpTable(
                        buffer,
                        ref size,
                        false,
                        AfInet6,
                        TcpTableOwnerPidAll,
                        0) != 0)
                        return false;

                    var rows = Marshal.ReadInt32(buffer);
                    var rowPtr = IntPtr.Add(buffer, sizeof(int));
                    var rowSize = Marshal.SizeOf(typeof(MibTcp6RowOwnerPid));

                    for (var i = 0; i < rows; i++)
                    {
                        var row = (MibTcp6RowOwnerPid)Marshal.PtrToStructure(
                            IntPtr.Add(rowPtr, i * rowSize),
                            typeof(MibTcp6RowOwnerPid));
                        Increment(counts, unchecked((int)row.OwningPid));
                    }

                    return true;
                }
                catch
                {
                    counts.Clear();
                    return false;
                }
                finally
                {
                    if (buffer != IntPtr.Zero)
                        Marshal.FreeHGlobal(buffer);
                }
            }

            private static void Merge(
                Dictionary<int, int> destination,
                Dictionary<int, int> source)
            {
                foreach (var pair in source)
                {
                    int count;
                    destination.TryGetValue(pair.Key, out count);
                    destination[pair.Key] = count + pair.Value;
                }
            }

            private static void Increment(Dictionary<int, int> counts, int pid)
            {
                int count;
                counts.TryGetValue(pid, out count);
                counts[pid] = count + 1;
            }
        }
    }
}
