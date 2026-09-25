using System;
using System.IO;
using System.Linq;
using EpsonRa.Bridge.Readiness;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Readiness.Tests
{
    [TestClass]
    public class WindowsReadinessEnvironmentTests
    {
        private string root;

        [TestInitialize]
        public void CreateRoot()
        {
            root = Path.Combine(Path.GetTempPath(), "epson-readiness-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(root);
        }

        [TestCleanup]
        public void DeleteRoot()
        {
            if (root != null && Directory.Exists(root))
            {
                Directory.Delete(root, true);
            }
        }

        [TestMethod]
        public void MissingExecutableAndApiAssemblyAreReportedMissing()
        {
            Directory.CreateDirectory(Path.Combine(root, "exe"));

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            Assert.AreEqual(CheckStatus.PRESENT, checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "rcPlusExecutable").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        [TestMethod]
        public void InvalidApiAssemblyBytesAreReportedInvalidWithoutLoadingCode()
        {
            PrepareExecutable();
            File.WriteAllBytes(ApiAssemblyPath(), new byte[] { 0, 1, 2, 3, 4 });

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            Assert.AreEqual(CheckStatus.INVALID, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        [TestMethod]
        public void ParseableNonRcapiAssemblyIsInvalid()
        {
            PrepareExecutable();
            File.Copy(typeof(ReadinessInspector).Assembly.Location, ApiAssemblyPath(), true);

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            var api = checks.Single(c => c.Name == "apiAssembly");
            Assert.AreEqual(CheckStatus.INVALID, api.Status);
            StringAssert.Contains(api.Detail, "RCAPINet");
        }

        [TestMethod]
        public void OriginalRcapiNamedFixtureProvidesPresentAssemblyMetadata()
        {
            PrepareExecutable();
            File.Copy(typeof(EpsonRa.Bridge.TestFixture.Marker).Assembly.Location, ApiAssemblyPath(), true);

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            var api = checks.Single(c => c.Name == "apiAssembly");
            Assert.AreEqual(CheckStatus.PRESENT, api.Status);
            StringAssert.Contains(api.Detail, "RCAPINet");
        }

        [TestMethod]
        public void MissingRootProducesMissingChecksWithoutFallback()
        {
            Directory.Delete(root, true);

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "rcPlusExecutable").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        private void PrepareExecutable()
        {
            var exe = Path.Combine(root, "exe");
            Directory.CreateDirectory(exe);
            File.WriteAllBytes(Path.Combine(exe, "erc70.exe"), new byte[] { 0 });
        }

        private string ApiAssemblyPath()
        {
            return Path.Combine(root, "exe", "RCAPINet.dll");
        }
    }
}
