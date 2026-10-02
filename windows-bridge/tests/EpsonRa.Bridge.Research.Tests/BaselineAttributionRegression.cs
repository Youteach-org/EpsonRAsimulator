using System;
using System.IO;
using EpsonRa.Bridge.Research.Supervisor;

namespace EpsonRa.Bridge.Research.Tests
{
    public static class BaselineAttributionRegression
    {
        public static void Run()
        {
            // No WorkerStarted call: no PID can yet be owned by this monitor.
            // Exercises real OS capture; PID 0 TCP rows must never become worker rows.
            using (var monitor = new SystemObservationMonitorFactory().Create(Path.GetTempPath()))
            {
                if (monitor.Before.OwnedProcessCount != 0 || monitor.Before.OwnedTcpCount != 0)
                    throw new InvalidOperationException("Before worker start, owned process/TCP counts must be zero; got " +
                        monitor.Before.OwnedProcessCount + "/" + monitor.Before.OwnedTcpCount);
            }
        }

        public static int Main()
        {
            try { Run(); Console.WriteLine("PASS: baseline owns no process or TCP rows"); return 0; }
            catch (Exception error) { Console.Error.WriteLine(error.Message); return 1; }
        }
    }
}
