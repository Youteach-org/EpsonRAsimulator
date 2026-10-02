using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class BaselineAttributionTests
    {
        [TestMethod]
        [Timeout(30000)]
        public void BeforeWorkerStartOwnsNoProcessOrTcpRows()
        {
            BaselineAttributionRegression.Run();
        }
    }
}
