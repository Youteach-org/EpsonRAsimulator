using EpsonRa.Bridge.Readiness;

namespace EpsonRa.Bridge.Inspect
{
    public static class Program
    {
        public static int Main(string[] args)
        {
            var environment = new WindowsReadinessEnvironment();
            var inspector = new ReadinessInspector(environment);
            var command = new InspectCommand(inspector);
            return command.Run(args, System.Console.Out, System.Console.Error);
        }
    }
}
