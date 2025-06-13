# BlueBeats

## About

BlueBeats is an Android App to play media and create highly flexible playlists.\
For a manual, visit my GitLab (see end of this document).

The highlight of this project is the concept of _dynamic playlists_.\
With them, you can define playlists by a set of rules and the list is assembled in any combination you can imagine.
Use directories, ID3-Tags, specific chapters and even Regex to define which file should be included.

This app is not meant to organize your Media-Library nor does it contain a Tag-Editor.

(This app is still in Beta, so expect crashes.)

## Dev Setup

1. clone this repo with ``--recurse-submodules``
2. copy `local.properties.template` to `local.properties` and set your values
3. open the project in Android Studio
4. run the Gradle task `App:taglib:lib_taglib generateInitialFiles`

## Repo

The GitHub Repo is a mirror from my GitLab.\
To get prebuilt binaries, go [here](https://projects.chocolatecakecodes.goip.de/bluebeats/bluebeats-app).
