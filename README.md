# Semantic Translation Enabler
The repository contains the source code of the ASSIST-IoT Semantic Translation Enabler (STE).

The only tool needed for compilation of the code is [SBT](http://www.scala-sbt.org/). All dependencies of the project will be automatically downloaded when `SBT` will be invoked for the first time.

## Building Docker image
To create a `Docker` image containing the latest version of the Semantic Translation Enabler, the user, from the `SBT` command prompt, needs to issue the command

```bash
docker
```

The command assumes that `Docker` is available on the develpment machine, and that the user has sufficient provileges to use it (without `sudo`).

The resulting image will be available from the local `Docker` registry under the name `assistiot/semantic_translation:n.n.n`, where `n.n.n` represents the current version of the enabler. 
The list of locally available images should be similar to:

```bash
user@devel-machine:~$ docker image ls
REPOSITORY                      TAG                 IMAGE ID            CREATED             SIZE
assistiot/semantic_translation  1.0.0               2a2587804717        1 minute ago        295MB
```
## Authors

- [Wiesław Pawłowski](mailto:wieslaw.pawlowski@ibspan.waw.pl)
- [Paweł Szmeja](mailto:pawel.szmeja@ibspan.waw.pl)
- [Piotr Sowiński](mailto:piotr.sowinski@ibspan.waw.pl)

## License

Apache 2.0
