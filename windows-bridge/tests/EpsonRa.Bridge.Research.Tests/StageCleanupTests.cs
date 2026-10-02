using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public sealed class StageCleanupTests
    {
        [DataTestMethod]
        [DataRow("Verify")]
        [DataRow("Disconnect")]
        [DataRow("InvalidPrior")]
        [DataRow("DuplicateName")]
        [DataRow("EventFailure")]
        public void FailedStagePreservesCleanup(string scenario) { StageCleanupRegression.Check(scenario); }
    }
}
