{
  description = "A very basic flake";

  inputs.flake-compat = {
    url = "github:edolstra/flake-compat";
    flake = false;
  };

  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    let
      overlay = final: prev: {
        randomcat.agorabot =
          let
            jdk = final.javaPackages.compiler.openjdk17;
            buildGradle = final.callPackage ./gradle-env.nix {};
            unwrappedBuild = buildGradle {
              envSpec = ./gradle-env.json;

              src = final.lib.cleanSourceWith {
                filter = final.lib.cleanSourceFilter;
                src = final.lib.cleanSourceWith {
                  filter = path: type: let baseName = baseNameOf path; in !(
                    (type == "directory" && (
                      baseName == "build" ||
                      baseName == ".idea" ||
                      baseName == ".gradle"
                    )) ||
                    (final.lib.hasSuffix ".iml" baseName)
                  );
                  src = ./.;
                };
              };

              gradleFlags = [ "installDist" "--no-watch-fs" ];

              buildJdk = jdk;

              installPhase = ''
                mkdir -p  -- "$out"
                cp -rT -- "build/install/AgoraBot" "$out"
              '';
           };
         in
         final.writeShellScriptBin "AgoraBot" ''
           export JAVA_HOME=${final.lib.escapeShellArg "${jdk.home}"}
           exec ${final.lib.escapeShellArg "${unwrappedBuild}/bin/AgoraBot"} "$@"
         '';
      };
    in
    { overlays.default = overlay; } //
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; overlays = [ overlay ]; };
      in
      rec {
        packages = rec {
          AgoraBot = pkgs.randomcat.agorabot;
          default = AgoraBot;
        };

        apps = rec {
          AgoraBot = {
            type = "app";
            program = "${packages.AgoraBot}/bin/AgoraBot";
          };

          default = AgoraBot;
        };
      }
    );
}
