using EpsonRa.Bridge.Research;
using Microsoft.VisualStudio.TestTools.UnitTesting;

namespace EpsonRa.Bridge.Research.Tests
{
    [TestClass]
    public class StagePolicyTests
    {
        [TestMethod]
        public void MetadataOnlyNeedsNoNativeAuthorizationOrTarget()
        {
            Assert.AreEqual(StageValidation.Valid,
                StagePolicy.Validate(Stage.MetadataOnly, null, null, false));
        }

        [TestMethod]
        public void EveryNativeStageRequiresSeparateAuthorization()
        {
            foreach (var stage in new[] { Stage.LoadOnly, Stage.InitializeObserve, Stage.Inventory, Stage.Connect })
                Assert.AreEqual(StageValidation.AuthorizationRequired,
                    StagePolicy.Validate(stage, "C4 Sample", 1, false));
        }

        [TestMethod]
        public void LoadOnlyMayRunWithoutActivatingTargetSelection()
        {
            Assert.AreEqual(StageValidation.Valid,
                StagePolicy.Validate(Stage.LoadOnly, null, null, true));
        }

        [TestMethod]
        public void ActivatingStagesRequireExactTargetAndExplicitInstance()
        {
            foreach (var stage in new[] { Stage.InitializeObserve, Stage.Inventory, Stage.Connect })
            {
                Assert.AreEqual(StageValidation.Valid,
                    StagePolicy.Validate(stage, "C4 Sample", 1, true));
                Assert.AreEqual(StageValidation.Valid,
                    StagePolicy.Validate(stage, "C4 Sample", 10, true));
                foreach (var target in new[] { null, "", " ", "c4 Sample", " C4 Sample", "C4 Sample " })
                    Assert.AreEqual(StageValidation.InvalidTarget,
                        StagePolicy.Validate(stage, target, 1, true));
                foreach (int? instance in new int?[] { null, 0, 11 })
                    Assert.AreEqual(StageValidation.InvalidServerInstance,
                        StagePolicy.Validate(stage, "C4 Sample", instance, true));
            }
        }

        [TestMethod]
        public void UnknownStageIsRejected()
        {
            Assert.AreEqual(StageValidation.InvalidStage,
                StagePolicy.Validate((Stage)999, "C4 Sample", 1, true));
        }
    }
}
