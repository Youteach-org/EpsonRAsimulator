namespace EpsonRa.Bridge.Research
{
    public enum Stage
    {
        MetadataOnly,
        LoadOnly,
        InitializeObserve,
        Inventory,
        Connect
    }

    public enum StageValidation
    {
        Valid,
        AuthorizationRequired,
        InvalidTarget,
        InvalidServerInstance,
        InvalidStage
    }

    public static class StagePolicy
    {
        private const string ExactTarget = "C4 Sample";

        public static StageValidation Validate(Stage stage, string target, int? serverInstance, bool authorized)
        {
            if (!System.Enum.IsDefined(typeof(Stage), stage))
                return StageValidation.InvalidStage;
            if (stage == Stage.MetadataOnly)
                return StageValidation.Valid;
            if (!authorized)
                return StageValidation.AuthorizationRequired;
            if (stage == Stage.LoadOnly)
                return StageValidation.Valid;
            if (target != ExactTarget)
                return StageValidation.InvalidTarget;
            if (!serverInstance.HasValue || serverInstance.Value < 1 || serverInstance.Value > 10)
                return StageValidation.InvalidServerInstance;
            return StageValidation.Valid;
        }
    }
}
