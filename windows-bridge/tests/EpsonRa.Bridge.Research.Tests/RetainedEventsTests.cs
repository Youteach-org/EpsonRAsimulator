using System;
using System.IO;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class RetainedEventsTests
    {
        [TestMethod]
        public void DurableFailuresRetainEventsWithoutPromotingPartialEvidence()
        {
            var configuration = new DirectoryInfo(AppDomain.CurrentDomain.BaseDirectory).Parent.Name;
            var fixture = Path.GetFullPath(Path.Combine(AppDomain.CurrentDomain.BaseDirectory,
                "..", "..", "..", "..", "EpsonRa.Bridge.Research.Fixture", "bin", configuration, "net48", "EpsonRa.Bridge.Research.Fixture.exe"));
            RetainedEventsRegression.Check(typeof(EpsonRa.Bridge.Research.Supervisor.WorkerSupervisor).Assembly.Location, fixture);
        }
    }
}
