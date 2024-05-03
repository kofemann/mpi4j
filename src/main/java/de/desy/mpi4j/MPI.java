package de.desy.mpi4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.foreign.ValueLayout.*;

public class MPI {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup MPILIB = SymbolLookup.libraryLookup("libmpi.so", Arena.global());

    private final static int MPI_MAX_ERROR_STRING = 256;

    private static final MethodHandle mpiInit;
    private static final MethodHandle mpiCommRank;
    private static final MethodHandle mpiCommSize;
    private static final MethodHandle mpiErrorString;
    private static final MethodHandle mpiFinalize;
    private static final MethodHandle mpiGather;
    private static final MethodHandle mpiBarrier;

    private static final MemorySegment MPI_DOUBLE;
    private static final MemorySegment MPI_COMM_WORLD;

    static {

        MPI_COMM_WORLD = MPILIB.find("ompi_mpi_comm_world").orElseThrow(() -> new NoSuchElementException("MPI_COMM_WORLD"));
        MPI_DOUBLE = MPILIB.find("ompi_mpi_double").orElseThrow(() -> new NoSuchElementException("MPI_DOUBLE"));

        mpiInit = LINKER.downcallHandle(
                MPILIB.find("MPI_Init").orElseThrow(() -> new NoSuchElementException("MPI_Init")),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
        );

        mpiCommRank = LINKER.downcallHandle(
                MPILIB.find("MPI_Comm_rank").orElseThrow(() -> new NoSuchElementException("MPI_Comm_rank")),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
        );

        mpiCommSize = LINKER.downcallHandle(
                MPILIB.find("MPI_Comm_size").orElseThrow(() -> new NoSuchElementException("MPI_Comm_size")),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS)
        );

        mpiErrorString = LINKER.downcallHandle(
                MPILIB.find("MPI_Error_string").orElseThrow(() -> new NoSuchElementException("MPI_Error_string")),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, ADDRESS)
        );

        mpiFinalize = LINKER.downcallHandle(
                MPILIB.find("MPI_Finalize").orElseThrow(() -> new NoSuchElementException("MPI_Finalize")),
                FunctionDescriptor.of(JAVA_INT)
        );

        mpiGather = LINKER.downcallHandle(
                MPILIB.find("MPI_Gather").orElseThrow(() -> new NoSuchElementException("MPI_Gather")),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS)
        );

        mpiBarrier = LINKER.downcallHandle(
                MPILIB.find("MPI_Barrier").orElseThrow(() -> new NoSuchElementException("MPI_Barrier")),
                FunctionDescriptor.of(JAVA_INT, ADDRESS)
        );

    }

    private MPI() {
        // utility class
    }

    public static void mpiInit(String[] args) throws MPIException {
        try (var arena = Arena.ofConfined()) {
            MemorySegment argc = arena.allocate(Integer.BYTES);
            argc.set(JAVA_INT, 0, args.length);

            MemorySegment argv = arena.allocate(ADDRESS.byteSize() * args.length);
            for (int i = 0; i < args.length; i++) {
                MemorySegment arg = arena.allocateFrom(args[i]);
                argv.set(ADDRESS, i * ADDRESS.byteSize(), arg);
            }
            int rc = (int) mpiInit.invokeExact(argc, argv);
            checkMpiError(rc);
        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void checkMpiError(int status) throws MPIException {
        if (status != 0) {

            int rc;
            try (var arena = Arena.ofConfined()) {
                MemorySegment len = arena.allocate(Integer.BYTES);
                MemorySegment msg = arena.allocate(MPI_MAX_ERROR_STRING);
                try {
                    rc = (int) mpiErrorString.invokeExact(status, msg, len);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                if (rc == 0) {
                    throw new MPIException(msg.getString(0));
                }
                throw new MPIException("MPI Error " + status);
            }
        }
    }

    public static int mpiCommRank() throws MPIException {
        try (var arena = Arena.ofConfined()) {
            MemorySegment rank = arena.allocate(Integer.BYTES);
            int rc = (int) mpiCommRank.invokeExact(MPI_COMM_WORLD, rank);
            checkMpiError(rc);
            return rank.asByteBuffer().order(ByteOrder.nativeOrder()).getInt();
        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public static int mpiCommSize() throws MPIException {
        try (var arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(Integer.BYTES);
            int rc = (int) mpiCommSize.invokeExact(MPI_COMM_WORLD, size);
            checkMpiError(rc);
            return size.asByteBuffer().order(ByteOrder.nativeOrder()).getInt();
        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void mpiFinalize() throws MPIException {
        try {
            int rc = (int) mpiFinalize.invokeExact();
            checkMpiError(rc);
        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public static void mpiGather(double send, double[] rcv) throws MPIException {
        try (var arena = Arena.ofConfined()) {
            var sendBuf = arena.allocate(Double.BYTES);
            sendBuf.set(JAVA_DOUBLE, 0, send);

            var rcvBuf = rcv.length == 0 ? MemorySegment.NULL : arena.allocate((long) Double.BYTES * rcv.length);
            int rc = (int) mpiGather.invokeExact(sendBuf, 1, MPI_DOUBLE, rcvBuf, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
            checkMpiError(rc);

            var b = rcvBuf.asByteBuffer().order(ByteOrder.nativeOrder()).asDoubleBuffer();
            for (int i = 0; i < b.capacity(); i++) {
                rcv[i] = b.get(i);
            }

        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void mpiBarrier() throws MPIException {
        try {
            int rc = (int) mpiBarrier.invokeExact(MPI_COMM_WORLD);
            checkMpiError(rc);
        } catch (MPIException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Throwable {

        System.out.println("=== MPI4J ===");

        MPI.mpiInit(args);

        int myRank = mpiCommRank();
        int size = mpiCommSize();
        double[] out = new double[0];
        if (myRank == 0) {
            System.out.println("size: " + size);
            out = new double[size];
        }

        mpiGather(ThreadLocalRandom.current().nextDouble(), out);
        mpiBarrier();
        if (myRank == 0) {
            for (int i = 0; i < out.length; i++) {
                System.out.println("out[" + i + "] = " + out[i]);
            }
        }
        mpiFinalize();
    }

}
