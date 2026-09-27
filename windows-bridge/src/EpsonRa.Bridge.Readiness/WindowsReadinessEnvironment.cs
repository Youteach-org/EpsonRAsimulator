using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Security;
using Microsoft.Win32;

namespace EpsonRa.Bridge.Readiness
{
    public sealed class WindowsReadinessEnvironment : IReadinessEnvironment
    {
        private const string ProductDisplayName = "EPSON RC+ 7.0";
        private const string UninstallPath = @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall";
        private readonly Func<string, FileAttributes> readAttributes;

        public WindowsReadinessEnvironment() : this(File.GetAttributes)
        {
        }

        public WindowsReadinessEnvironment(Func<string, FileAttributes> readAttributes)
        {
            this.readAttributes = readAttributes ?? throw new ArgumentNullException(nameof(readAttributes));
        }

        public IReadOnlyList<string> DiscoverRoots()
        {
            var roots = new List<string>();
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                try
                {
                    using (var baseKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                    using (var uninstall = baseKey.OpenSubKey(UninstallPath, false))
                    {
                        if (uninstall == null)
                        {
                            continue;
                        }

                        foreach (var subKeyName in uninstall.GetSubKeyNames())
                        {
                            using (var product = uninstall.OpenSubKey(subKeyName, false))
                            {
                                if (product == null)
                                {
                                    continue;
                                }

                                var displayName = product.GetValue("DisplayName") as string;
                                if (!string.Equals(displayName, ProductDisplayName, StringComparison.OrdinalIgnoreCase))
                                {
                                    continue;
                                }

                                var installLocation = product.GetValue("InstallLocation") as string;
                                if (!string.IsNullOrWhiteSpace(installLocation))
                                {
                                    roots.Add(installLocation);
                                }
                            }
                        }
                    }
                }
                catch (SecurityException ex)
                {
                    throw new UnauthorizedAccessException("EPSON RC+ registry discovery access denied.", ex);
                }
            }

            return roots.ToArray();
        }

        public IReadOnlyList<ReadinessCheck> InspectRoot(string root)
        {
            var checks = new List<ReadinessCheck>();
            if (string.IsNullOrWhiteSpace(root))
            {
                checks.Add(new ReadinessCheck("installRoot", CheckStatus.INVALID, "Install root is blank."));
                checks.Add(new ReadinessCheck("rcPlusExecutable", CheckStatus.MISSING, "erc70.exe was not inspected."));
                checks.Add(new ReadinessCheck("apiAssembly", CheckStatus.MISSING, "RCAPINet.dll was not inspected."));
                checks.Add(new ReadinessCheck("rcPlusVersion", CheckStatus.UNVERIFIED, "RC+ product version was not inspected."));
                return checks;
            }

            bool rootExists;
            try
            {
                rootExists = (readAttributes(root) & FileAttributes.Directory) != 0;
            }
            catch (FileNotFoundException)
            {
                rootExists = false;
            }
            catch (DirectoryNotFoundException)
            {
                rootExists = false;
            }
            catch (Exception ex) when (IsExpectedMetadataException(ex))
            {
                checks.Add(new ReadinessCheck("installRoot", CheckStatus.INVALID, "Install root metadata could not be read."));
                checks.Add(new ReadinessCheck("rcPlusExecutable", CheckStatus.MISSING, "erc70.exe was not inspected."));
                checks.Add(new ReadinessCheck("apiAssembly", CheckStatus.MISSING, "RCAPINet.dll was not inspected."));
                checks.Add(new ReadinessCheck("rcPlusVersion", CheckStatus.UNVERIFIED, "RC+ product version was not inspected."));
                return checks;
            }

            if (!rootExists)
            {
                checks.Add(new ReadinessCheck("installRoot", CheckStatus.MISSING, "Install root directory is absent."));
                checks.Add(new ReadinessCheck("rcPlusExecutable", CheckStatus.MISSING, "erc70.exe is absent because the install root is missing."));
                checks.Add(new ReadinessCheck("apiAssembly", CheckStatus.MISSING, "RCAPINet.dll is absent because the install root is missing."));
                checks.Add(new ReadinessCheck("rcPlusVersion", CheckStatus.UNVERIFIED, "RC+ product version is not inferred from files."));
                return checks;
            }

            checks.Add(new ReadinessCheck("installRoot", CheckStatus.PRESENT, "Install root directory is present."));

            var exePath = Path.Combine(root, "exe", "erc70.exe");
            checks.Add(InspectExecutable(exePath));

            var apiPath = Path.Combine(root, "exe", "RCAPINet.dll");
            checks.Add(InspectApiAssembly(apiPath));

            string version;
            try
            {
                version = FindProductVersion(root);
            }
            catch (Exception ex) when (IsExpectedMetadataException(ex))
            {
                version = null;
            }

            checks.Add(string.IsNullOrWhiteSpace(version)
                ? new ReadinessCheck("rcPlusVersion", CheckStatus.UNVERIFIED, "RC+ product version was not available from matching uninstall metadata.")
                : new ReadinessCheck("rcPlusVersion", CheckStatus.PRESENT, "EPSON RC+ product version " + version + "."));

            return checks;
        }

        private ReadinessCheck InspectExecutable(string path)
        {
            try
            {
                FileAttributes attributes;
                try
                {
                    attributes = readAttributes(path);
                }
                catch (FileNotFoundException)
                {
                    return new ReadinessCheck("rcPlusExecutable", CheckStatus.MISSING, "erc70.exe is missing.");
                }
                catch (DirectoryNotFoundException)
                {
                    return new ReadinessCheck("rcPlusExecutable", CheckStatus.MISSING, "erc70.exe is missing.");
                }

                if ((attributes & FileAttributes.Directory) != 0)
                {
                    return new ReadinessCheck("rcPlusExecutable", CheckStatus.INVALID, "erc70.exe path is a directory.");
                }

                var info = new FileInfo(path);
                var detail = "erc70.exe present; size=" + info.Length + " bytes";
                try
                {
                    var version = FileVersionInfo.GetVersionInfo(path).FileVersion;
                    if (!string.IsNullOrWhiteSpace(version))
                    {
                        detail += "; fileVersion=" + version;
                    }
                }
                catch (Exception ex) when (IsExpectedMetadataException(ex))
                {
                    detail += "; fileVersion=unavailable";
                }

                return new ReadinessCheck("rcPlusExecutable", CheckStatus.PRESENT, detail + ".");
            }
            catch (Exception ex) when (IsExpectedMetadataException(ex))
            {
                return new ReadinessCheck("rcPlusExecutable", CheckStatus.INVALID, "erc70.exe metadata could not be read.");
            }
        }

        private ReadinessCheck InspectApiAssembly(string path)
        {
            try
            {
                FileAttributes attributes;
                try
                {
                    attributes = readAttributes(path);
                }
                catch (FileNotFoundException)
                {
                    return new ReadinessCheck("apiAssembly", CheckStatus.MISSING, "RCAPINet.dll is missing.");
                }
                catch (DirectoryNotFoundException)
                {
                    return new ReadinessCheck("apiAssembly", CheckStatus.MISSING, "RCAPINet.dll is missing.");
                }

                if ((attributes & FileAttributes.Directory) != 0)
                {
                    return new ReadinessCheck("apiAssembly", CheckStatus.INVALID, "RCAPINet.dll path is a directory.");
                }

                var fileInfo = new FileInfo(path);
                var assemblyName = AssemblyName.GetAssemblyName(path);
                if (!string.Equals(assemblyName.Name, "RCAPINet", StringComparison.Ordinal))
                {
                    return new ReadinessCheck(
                        "apiAssembly",
                        CheckStatus.INVALID,
                        "Expected assembly simple name RCAPINet but found " + (assemblyName.Name ?? "<null>") + ".");
                }

                var detail = "RCAPINet assembly metadata present; assemblyVersion=" +
                    (assemblyName.Version == null ? "unavailable" : assemblyName.Version.ToString()) +
                    "; size=" + fileInfo.Length + " bytes.";

                return new ReadinessCheck("apiAssembly", CheckStatus.PRESENT, detail);
            }
            catch (BadImageFormatException)
            {
                return new ReadinessCheck("apiAssembly", CheckStatus.INVALID, "RCAPINet.dll is not a valid managed assembly image.");
            }
            catch (FileLoadException)
            {
                return new ReadinessCheck("apiAssembly", CheckStatus.INVALID, "RCAPINet.dll assembly metadata could not be read.");
            }
            catch (Exception ex) when (IsExpectedMetadataException(ex))
            {
                return new ReadinessCheck("apiAssembly", CheckStatus.INVALID, "RCAPINet.dll metadata could not be read.");
            }
        }

        private static string FindProductVersion(string root)
        {
            foreach (var view in new[] { RegistryView.Registry64, RegistryView.Registry32 })
            {
                using (var baseKey = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, view))
                using (var uninstall = baseKey.OpenSubKey(UninstallPath, false))
                {
                    if (uninstall == null)
                    {
                        continue;
                    }

                    foreach (var subKeyName in uninstall.GetSubKeyNames())
                    {
                        using (var product = uninstall.OpenSubKey(subKeyName, false))
                        {
                            if (product == null)
                            {
                                continue;
                            }

                            var displayName = product.GetValue("DisplayName") as string;
                            var installLocation = product.GetValue("InstallLocation") as string;
                            if (!string.Equals(displayName, ProductDisplayName, StringComparison.OrdinalIgnoreCase) ||
                                !SameRoot(root, installLocation))
                            {
                                continue;
                            }

                            return product.GetValue("DisplayVersion") as string;
                        }
                    }
                }
            }

            return null;
        }

        private static bool SameRoot(string left, string right)
        {
            if (string.IsNullOrWhiteSpace(left) || string.IsNullOrWhiteSpace(right))
            {
                return false;
            }

            try
            {
                return string.Equals(
                    NormalizeRoot(left),
                    NormalizeRoot(right),
                    StringComparison.OrdinalIgnoreCase);
            }
            catch (Exception ex) when (IsExpectedMetadataException(ex))
            {
                return false;
            }
        }

        private static string NormalizeRoot(string value)
        {
            var full = Path.GetFullPath(value);
            var pathRoot = Path.GetPathRoot(full);
            return string.Equals(full, pathRoot, StringComparison.OrdinalIgnoreCase)
                ? full
                : full.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        }

        private static bool IsExpectedMetadataException(Exception ex)
        {
            return ex is UnauthorizedAccessException ||
                   ex is IOException ||
                   ex is ArgumentException ||
                   ex is NotSupportedException ||
                   ex is PathTooLongException ||
                   ex is SecurityException ||
                   ex is Win32Exception;
        }
    }
}

