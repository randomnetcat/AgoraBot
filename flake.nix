{
  description = "A very basic flake";

  inputs.flake-compat = {
    url = "github:edolstra/flake-compat";
    flake = false;
  };

  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }: flake-utils.lib.eachDefaultSystem (system:
    let
      pkgs = nixpkgs.legacyPackages."${system}";
    in
    rec {
      packages = {
        AgoraBot =
          let
            buildGradle = pkgs.callPackage ./gradle-env.nix {};
            unwrappedBuild = buildGradle {
              envSpec = ./gradle-env.json;

              src = pkgs.lib.cleanSourceWith {
                filter = pkgs.lib.cleanSourceFilter;
                src = pkgs.lib.cleanSourceWith {
                  filter = path: type: let baseName = baseNameOf path; in !(
                    (type == "directory" && (
                      baseName == "build" ||
                      baseName == ".idea" ||
                      baseName == ".gradle"
                    )) ||
                    (pkgs.lib.hasSuffix ".iml" baseName)
                  );
                  src = ./.;
                };
              };

              gradleFlags = [ "installDist" ];

              buildJdk = pkgs.jdk11;

              installPhase = ''
                mkdir -p  -- "$out"
                cp -rT -- "build/install/AgoraBot" "$out"
              '';
           };
         in
         pkgs.writeShellScriptBin "AgoraBot" ''
           export JAVA_HOME=${pkgs.lib.escapeShellArg "${pkgs.jdk.home}"}
           exec ${pkgs.lib.escapeShellArg "${unwrappedBuild}/bin/AgoraBot"} "$@"
         '';
      };

      defaultPackage = packages.AgoraBot;

      apps = {
        AgoraBot = {
          type = "app";
          program = "${packages.AgoraBot}/bin/AgoraBot";
        };
      };

      defaultApp = apps.AgoraBot;
    }
  );
}
