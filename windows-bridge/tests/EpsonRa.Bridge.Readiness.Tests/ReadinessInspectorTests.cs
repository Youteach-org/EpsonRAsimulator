using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Readiness.Tests
{
    [TestClass]
    public class ReadinessInspectorTests
    {
        [TestMethod]
        public void ExplicitMissingRootDoesNotFallBackToDiscovery()
        {
            var env = new FakeEnvironment(new[] { @"C:\Other" });
            env.Results[@"C:\Missing"] = new[] {
                new ReadinessCheck("installRoot", CheckStatus.MISSING, "Directory absent")
            };

            var report = new ReadinessInspector(env).Inspect(@"C:\Missing");

            Assert.IsFalse(report.Ready);
            Assert.AreEqual(@"C:\Missing", report.InstallRoot);
            CollectionAssert.AreEqual(new[] { @"C:\Missing" }, env.InspectedRoots.ToArray());
            Assert.AreEqual(0, env.DiscoveryCalls);
        }

        [TestMethod]
        public void NoDiscoveredRootsReportsMissingWithoutInspection()
        {
            var env = new FakeEnvironment(Array.Empty<string>());

            var report = new ReadinessInspector(env).Inspect(null);

            Assert.IsFalse(report.Ready);
            Assert.IsNull(report.InstallRoot);
            Assert.AreEqual(CheckStatus.MISSING, report.Checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(1, env.DiscoveryCalls);
            Assert.AreEqual(0, env.InspectedRoots.Count);
        }

        [TestMethod]
        public void TwoDistinctDiscoveredRootsAreAmbiguousWithoutInspection()
        {
            var env = new FakeEnvironment(new[] { @"C:\EpsonRC70", @"D:\EpsonRC70" });

            var report = new ReadinessInspector(env).Inspect(null);

            Assert.IsFalse(report.Ready);
            Assert.IsNull(report.InstallRoot);
            Assert.AreEqual(CheckStatus.INVALID, report.Checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(1, env.DiscoveryCalls);
            Assert.AreEqual(0, env.InspectedRoots.Count);
        }

        [TestMethod]
        public void EquivalentDiscoveredRootsNormalizeAndInspectOnce()
        {
            var env = new FakeEnvironment(new[] { @"C:\EpsonRC70\", @"c:\epsonrc70" });
            env.Results[@"C:\EpsonRC70"] = PresentInstallation();

            var report = new ReadinessInspector(env).Inspect(null);

            Assert.IsTrue(report.Ready);
            Assert.AreEqual(@"C:\EpsonRC70", report.InstallRoot);
            Assert.AreEqual(1, env.DiscoveryCalls);
            Assert.AreEqual(1, env.InspectedRoots.Count);
            Assert.AreEqual(@"C:\EpsonRC70", env.InspectedRoots[0]);
        }

        [TestMethod]
        public void WhitespaceExplicitRootIsInvalidWithoutFallback()
        {
            var env = new FakeEnvironment(new[] { @"C:\Other" });

            var report = new ReadinessInspector(env).Inspect("   ");

            Assert.IsFalse(report.Ready);
            Assert.AreEqual(CheckStatus.INVALID, report.Checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(0, env.DiscoveryCalls);
            Assert.AreEqual(0, env.InspectedRoots.Count);
        }

        [TestMethod]
        public void RelativeExplicitRootIsInvalidWithoutFallback()
        {
            var env = new FakeEnvironment(new[] { @"C:\Other" });

            var report = new ReadinessInspector(env).Inspect(@"EpsonRC70");

            Assert.IsFalse(report.Ready);
            Assert.AreEqual(CheckStatus.INVALID, report.Checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(0, env.DiscoveryCalls);
            Assert.AreEqual(0, env.InspectedRoots.Count);
        }

        [TestMethod]
        public void UnreadableExplicitRootBecomesInvalidWithoutFallback()
        {
            var env = new FakeEnvironment(new[] { @"C:\Other" });
            env.Exceptions[@"C:\Denied"] = new UnauthorizedAccessException("denied");

            var report = new ReadinessInspector(env).Inspect(@"C:\Denied");

            Assert.IsFalse(report.Ready);
            Assert.AreEqual(@"C:\Denied", report.InstallRoot);
            Assert.AreEqual(CheckStatus.INVALID, report.Checks.Single(c => c.Name == "installRoot").Status);
            Assert.AreEqual(0, env.DiscoveryCalls);
            CollectionAssert.AreEqual(new[] { @"C:\Denied" }, env.InspectedRoots.ToArray());
        }

        [TestMethod]
        public void ReportCopiesEnvironmentChecksAndAppendsUnverifiedNativeFacts()
        {
            var mutable = new List<ReadinessCheck>(PresentInstallation());
            var env = new FakeEnvironment(new[] { @"C:\EpsonRC70" });
            env.Results[@"C:\EpsonRC70"] = mutable;

            var report = new ReadinessInspector(env).Inspect(@"C:\EpsonRC70");
            mutable.Clear();

            Assert.IsTrue(report.Ready);
            Assert.AreEqual(CheckStatus.UNVERIFIED, report.Checks.Single(c => c.Name == "license").Status);
            Assert.AreEqual(CheckStatus.UNVERIFIED, report.Checks.Single(c => c.Name == "nativeRuntime").Status);
            Assert.IsTrue(report.Checks.Any(c => c.Name == "apiAssembly"));
        }

        [TestMethod]
        public void UnverifiedRequiredInstallationCheckPreventsReady()
        {
            var env = new FakeEnvironment(new[] { @"C:\EpsonRC70" });
            env.Results[@"C:\EpsonRC70"] = new[] {
                new ReadinessCheck("installRoot", CheckStatus.PRESENT, "Directory present"),
                new ReadinessCheck("apiAssembly", CheckStatus.UNVERIFIED, "Metadata unavailable")
            };

            var report = new ReadinessInspector(env).Inspect(@"C:\EpsonRC70");

            Assert.IsFalse(report.Ready);
        }

        private static IReadOnlyList<ReadinessCheck> PresentInstallation()
        {
            return new[] {
                new ReadinessCheck("installRoot", CheckStatus.PRESENT, "Directory present"),
                new ReadinessCheck("rcPlusExecutable", CheckStatus.PRESENT, "Executable present"),
                new ReadinessCheck("apiAssembly", CheckStatus.PRESENT, "Assembly metadata present")
            };
        }

        private sealed class FakeEnvironment : IReadinessEnvironment
        {
            private readonly IReadOnlyList<string> roots;

            public FakeEnvironment(IReadOnlyList<string> roots)
            {
                this.roots = roots;
            }

            public Dictionary<string, IReadOnlyList<ReadinessCheck>> Results { get; } =
                new Dictionary<string, IReadOnlyList<ReadinessCheck>>(StringComparer.OrdinalIgnoreCase);

            public Dictionary<string, Exception> Exceptions { get; } =
                new Dictionary<string, Exception>(StringComparer.OrdinalIgnoreCase);

            public List<string> InspectedRoots { get; } = new List<string>();

            public int DiscoveryCalls { get; private set; }

            public IReadOnlyList<string> DiscoverRoots()
            {
                DiscoveryCalls++;
                return roots.ToArray();
            }

            public IReadOnlyList<ReadinessCheck> InspectRoot(string root)
            {
                InspectedRoots.Add(root);
                Exception error;
                if (Exceptions.TryGetValue(root, out error))
                {
                    throw error;
                }

                IReadOnlyList<ReadinessCheck> result;
                return Results.TryGetValue(root, out result)
                    ? result
                    : Array.Empty<ReadinessCheck>();
            }
        }
    }
}
