package de.desy.mpi4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.NoSuchElementException;
import java.nio.ByteOrder;

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
    private static final MemorySegment MPI_COMM_WORLD;

    static {

        MPI_COMM_WORLD = MPILIB.find("ompi_mpi_comm_world").orElseThrow(() -> new NoSuchElementException("MPI_COMM_WORLD"));

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

    }

    private MPI() {
        // utility class
    }

    public static void mpiInit(String[] args) throws MPIException {
        int rc;
        try (var arena = Arena.ofConfined()) {
            MemorySegment argc = arena.allocate(Integer.BYTES);
            argc.set(JAVA_INT, 0, args.length);

            MemorySegment argv = arena.allocate(ADDRESS.byteSize() * args.length);
            for (int i = 0; i < args.length; i++) {
                MemorySegment arg = arena.allocateFrom(args[i]);
                argv.set(ADDRESS, i * ADDRESS.byteSize(), arg);
            }
            rc = (int) mpiInit.invokeExact(argc, argv);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        checkMpiError(rc);
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
        int rc;
        int r;
        try (var arena = Arena.ofConfined()) {
            MemorySegment rank = arena.allocate(Integer.BYTES);
            rc = (int) mpiCommRank.invokeExact(MPI_COMM_WORLD, rank);
            r = rank.asByteBuffer().order(ByteOrder.nativeOrder()).getInt();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        checkMpiError(rc);
        return r;
    }


    public static int mpiCommSize() throws MPIException {
        int rc;
        int s;
        try (var arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(Integer.BYTES);
            rc = (int) mpiCommSize.invokeExact(MPI_COMM_WORLD, size);
            s = size.asByteBuffer().order(ByteOrder.nativeOrder()).getInt();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        checkMpiError(rc);
        return s;
    }

    public static void mpiFinalize() throws MPIException {
        int rc;
        try {
            rc = (int) mpiFinalize.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        checkMpiError(rc);
    }

    public static void main(String[] args) throws Throwable {

        System.out.println("=== MPI4J ===");

        MPI.mpiInit(args);

        System.out.println("size: " + mpiCommSize());
        System.out.println("rank: " + mpiCommRank());
        mpiFinalize();

    }

}
