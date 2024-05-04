package de.desy.mpi4j;

import java.util.concurrent.ThreadLocalRandom;

public class App {

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
        MPI.mpiFinalize();
    }

}
