using System;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class DurableCaptureTests
    {
        [TestMethod]
        public void IndependentProcessPersistsResultWithoutWaitingForDescendantPipes()
        {
            var supervisor = typeof(EpsonRa.Bridge.Research.Supervisor.WorkerSupervisor).Assembly.Location;
            var configuration = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory).Parent.Name;
            var fixture = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..", "EpsonRa.Bridge.Research.Fixture", "bin", configuration, "net48", "EpsonRa.Bridge.Research.Fixture.exe"));
            DurableCaptureRegression.Check(supervisor, fixture);
        }
    }
}
