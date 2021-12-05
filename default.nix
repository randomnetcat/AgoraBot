{ pkgs ? import <nixpkgs> {} }:
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
  ''
