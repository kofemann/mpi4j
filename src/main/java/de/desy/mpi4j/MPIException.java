package de.desy.mpi4j;

import java.io.IOException;

public class MPIException extends IOException {

   /**
    * Constructs an MPIException with the specified detail message.
    * @parm message The MPI error message.
    */
   public MPIException(String message) {
     super(message);
   }

}
