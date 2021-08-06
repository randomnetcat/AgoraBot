{ pkgs ? import <nixpkgs> {} }:
let
  buildGradle = pkgs.callPackage ./gradle-env.nix {};
  unwrappedBuild = buildGradle {
    envSpec = ./gradle-env.json;

    src = ./.;

    gradleFlags = [ "installDist" ];

    buildJdk = pkgs.jdk;

    installPhase = ''
      mkdir -p  -- "$out"
      cp -rT -- "build/install/AgoraBot" "$out"
    '';
 };
in
  pkgs.writeShellScriptBin "AgoraBot" ''
    export JAVA_HOME=${pkgs.lib.escapeShellArg "${pkgs.jdk.home}"}
    ${pkgs.lib.escapeShellArg "${unwrappedBuild}/bin/AgoraBot"} "$@"
  ''
