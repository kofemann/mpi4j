# MPI4J

Java FFI based binding for MPI

## Requirments

- java 22

## How to run

The `MPI4J` uses Java's [Foreign Function Inferface][1]. To run access it the application must be started with a corresponding command line options:

```
$ export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib64/openmpi/lib
$ $JAVA_HOME/bin/java --enable-native-access=ALL-UNNAMED -cp ... App
```

## Example

```java
    public static void main(String[] args) throws Throwable {

    System.out.println("=== MPI4J ===");

    MPI.mpiInit(args);

    int myRank = MPI.world().mpiCommRank();
    int size = MPI.world().mpiCommSize();
    double[] out = new double[0];
    if (myRank == 0) {
        System.out.println("size: " + size);
        out = new double[size];
    }

    MPI.world().mpiGather(ThreadLocalRandom.current().nextDouble(), out);
    MPI.world().mpiBarrier();
    if (myRank == 0) {
        for (int i = 0; i < out.length; i++) {
            System.out.println("out[" + i + "] = " + out[i]);
        }
    }
    mpiFinalize();
}
```


## Status

This project is a proof of concept and not expected to be used (at least now) for real application.
Nevertheless, pull-requests are welcome!

## Usage with JDK 9 module system

With the provided stable automatic module name __de.desy.mpi4j__, **mpi4j**
can be used in modular java9 application:

```java
module com.foo.bar {
    requires de.desy.mpi4j;
}
```

## How to contribute

**MPI4J** uses the linux kernel model where git is not only source repository,
but also the way to track contributions and copyrights.

Each submitted patch must have a "Signed-off-by" line.  Patches without
this line will not be accepted.

The sign-off is a simple line at the end of the explanation for the
patch, which certifies that you wrote it or otherwise have the right to
pass it on as an open-source patch.  The rules are pretty simple: if you
can certify the below:
```

    Developer's Certificate of Origin 1.1

    By making a contribution to this project, I certify that:

    (a) The contribution was created in whole or in part by me and I
         have the right to submit it under the open source license
         indicated in the file; or

    (b) The contribution is based upon previous work that, to the best
        of my knowledge, is covered under an appropriate open source
        license and I have the right under that license to submit that
        work with modifications, whether created in whole or in part
        by me, under the same open source license (unless I am
        permitted to submit under a different license), as indicated
        in the file; or

    (c) The contribution was provided directly to me by some other
        person who certified (a), (b) or (c) and I have not modified
        it.

    (d) I understand and agree that this project and the contribution
        are public and that a record of the contribution (including all
        personal information I submit with it, including my sign-off) is
        maintained indefinitely and may be redistributed consistent with
        this project or the open source license(s) involved.

```
then you just add a line saying ( git commit -s )

    Signed-off-by: Random J Developer <random@developer.example.org>

using your real name (sorry, no pseudonyms or anonymous contributions.)


## License

This work is published under MIT License

[1]: https://docs.oracle.com/en/java/javase/22/core/foreign-function-and-memory-api.html