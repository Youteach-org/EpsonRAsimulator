using System;
using System.Diagnostics;
using System.Threading.Tasks;

namespace EpsonRa.Bridge.Research.Supervisor
{
    // At most one observation operation is in flight. A stalled OS call cannot hold
    // the parent deadline; its background continuation owns deferred monitor cleanup.
    internal sealed class ObservationDeadline
    {
        private readonly Stopwatch clock;
        private readonly int timeoutMs;
        private Task pending = Task.FromResult(0);
        public ObservationDeadline(Stopwatch clock, int seconds) { this.clock = clock; timeoutMs = seconds * 1000; }
        public T Run<T>(Func<T> operation)
        {
            var remaining = timeoutMs - (int)Math.Min(int.MaxValue, clock.ElapsedMilliseconds);
            if (remaining <= 0) throw new TimeoutException();
            var task = Task.Run(operation);
            pending = task;
            if (Task.WaitAny(new Task[] { task }, remaining) < 0) throw new TimeoutException();
            if (clock.ElapsedMilliseconds >= timeoutMs) throw new TimeoutException();
            return task.GetAwaiter().GetResult();
        }
        public void Run(Action operation) { Run(() => { operation(); return true; }); }
        public void Release(Action cleanup)
        {
            pending.ContinueWith(previous => { var ignored = previous.Exception; try { cleanup(); } catch { } }, TaskScheduler.Default);
        }
    }
}

