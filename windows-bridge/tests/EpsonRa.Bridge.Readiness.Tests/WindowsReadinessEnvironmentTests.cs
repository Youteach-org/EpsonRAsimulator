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
            Assert.IsFalse(IsFixtureAssemblyLoaded());
            File.Copy(FixtureAssemblyPath(), ApiAssemblyPath(), true);

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);

            var api = checks.Single(c => c.Name == "apiAssembly");
            Assert.AreEqual(CheckStatus.PRESENT, api.Status);
            StringAssert.Contains(api.Detail, "RCAPINet");
            Assert.IsFalse(IsFixtureAssemblyLoaded(), "Metadata inspection must not load RCAPINet into the AppDomain.");
        }

        [TestMethod]
        public void UnavailableRegistryVersionIsInformationalAndDoesNotBlockReady()
        {
            PrepareExecutable();
            File.Copy(FixtureAssemblyPath(), ApiAssemblyPath(), true);

            var checks = new WindowsReadinessEnvironment().InspectRoot(root);
            var report = new ReadinessReport(root, checks);

            Assert.AreEqual(CheckStatus.UNVERIFIED, checks.Single(c => c.Name == "rcPlusVersion").Status);
            Assert.IsTrue(report.Ready);
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

        [TestMethod]
        public void DeniedRootMetadataIsInvalidRatherThanMissing()
        {
            var environment = new WindowsReadinessEnvironment(path =>
            {
                if (path == root) throw new UnauthorizedAccessException("denied");
                return File.GetAttributes(path);
            });

            var checks = environment.InspectRoot(root);

            Assert.AreEqual(CheckStatus.INVALID, checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "rcPlusExecutable").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        [TestMethod]
        public void DeniedExecutableMetadataIsInvalidRatherThanMissing()
        {
            Directory.CreateDirectory(Path.Combine(root, "exe"));
            var executable = Path.Combine(root, "exe", "erc70.exe");
            var environment = new WindowsReadinessEnvironment(path =>
            {
                if (path == executable) throw new UnauthorizedAccessException("denied");
                return File.GetAttributes(path);
            });

            var checks = environment.InspectRoot(root);

            Assert.AreEqual(CheckStatus.PRESENT, checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(CheckStatus.INVALID, checks.Single(c => c.Name == "rcPlusExecutable").Status);
            Assert.AreEqual(CheckStatus.MISSING, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        [TestMethod]
        public void ApiMetadataIoErrorIsInvalidRatherThanMissing()
        {
            PrepareExecutable();
            var api = ApiAssemblyPath();
            var environment = new WindowsReadinessEnvironment(path =>
            {
                if (path == api) throw new IOException("metadata failed");
                return File.GetAttributes(path);
            });

            var checks = environment.InspectRoot(root);

            Assert.AreEqual(CheckStatus.PRESENT, checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(CheckStatus.PRESENT, checks.Single(c => c.Name == "rcPlusExecutable").Status);
            Assert.AreEqual(CheckStatus.INVALID, checks.Single(c => c.Name == "apiAssembly").Status);
        }

        private static string FixtureAssemblyPath()
        {
            var path = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "RCAPINet.dll");
            Assert.IsTrue(File.Exists(path), "The original RCAPINet-named fixture must be copied to test output.");
            return path;
        }

        private static bool IsFixtureAssemblyLoaded()
        {
            return AppDomain.CurrentDomain.GetAssemblies()
                .Any(assembly => string.Equals(assembly.GetName().Name, "RCAPINet", StringComparison.Ordinal));
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

