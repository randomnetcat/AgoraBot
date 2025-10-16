{
  description = "A very basic flake";
  inputs.flake-compat = {
    url = "github:edolstra/flake-compat";
    flake = false;
  };

  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
  };

  inputs.gradle2nix = {
    url = "github:tadfisher/gradle2nix/v2";
    inputs.nixpkgs.follows = "nixpkgs";
  };

  outputs = { self, nixpkgs, flake-utils, gradle2nix, ... }:
    let
      overlay = final: prev: {
        randomcat.agorabot =
          let
            jdk = final.javaPackages.compiler.openjdk17;
            buildGradle = final.callPackage ./gradle-env.nix {};
            unwrappedBuild = gradle2nix.builders.${final.buildPlatform.system}.buildGradlePackage {
              pname = "agorabot";
              version = "1.0-SNAPSHOT";

              src = ./.;
              lockFile = ./gradle.lock;

              buildJdk = jdk;

              gradleFlags = [
                "--console=plain"
                "--no-daemon"
                "--no-watch-fs"
                "--parallel"
                "-Dorg.gradle.java.home=${jdk.home}"
              ];

              buildPhase = ''
                set -x
                gradle assemble $gradleFlags
              '';

              checkPhase = ''
                gradle check $gradleFlags
              '';

              installPhase = ''
                gradle installDist $gradleFlags
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
