package de.desy.mpi4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.NoSuchElementException;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.*;


public class MPI {

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup MPILIB = SymbolLookup.libraryLookup("libmpi.so", Arena.global());


  private static final MethodHandle mpiInit;
  private static final MethodHandle mpiCommRank;
  private static final MethodHandle mpiCommSize;
  private static final MethodHandle mpiFinalize;
  private static final MemorySegment MPI_COMM_WORLD;

  static {

        MPI_COMM_WORLD =  MPILIB.find("ompi_mpi_comm_world").orElseThrow(() -> new NoSuchElementException("MPI_COMM_WORLD"));

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

        mpiFinalize = LINKER.downcallHandle(
            MPILIB.find("MPI_Finalize").orElseThrow(() -> new NoSuchElementException("MPI_Finalize")),
                    FunctionDescriptor.of(JAVA_INT)
            );
     
  }

  public static void main(String[] args) throws Throwable {
    
    System.out.println("=== MPI4J ===");

    try (var arena = Arena.ofConfined()) {
      System.out.println(mpiInit);
      int rc = (int)mpiInit.invoke(MemorySegment.NULL, MemorySegment.NULL);
      System.out.println(rc);

      MemorySegment size = arena.allocate(4);
      rc = (int)mpiCommSize.invoke(MPI_COMM_WORLD, size);
      System.out.println("size rc: " + rc);
      System.out.println("size: " + size.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN).getInt());


      MemorySegment rank = arena.allocate(4);
      rc = (int)mpiCommRank.invoke(MPI_COMM_WORLD, rank);
      System.out.println("rank rc: " + rc);
      System.out.println("rank: " + rank.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN).getInt());


      rc =(int) mpiFinalize.invokeExact();
      System.out.println("finalize rc: " + rc);
    }
    
  }

}
