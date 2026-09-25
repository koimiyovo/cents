package android.database;

/**
 * A stand-in for Android's own class, for the unit tests that run on the computer's JVM.
 *
 * There, "android.jar" only holds empty stubs of the Android classes, and the stub of this exception
 * forgets its message. Room needs that message: an upsert is an insert that, when the id exists
 * already, fails with a "UNIQUE constraint" error, which Room recognises by its text before falling
 * back to an update. Without the message every upsert of an existing row fails.
 *
 * This copy comes first on the test classpath and keeps the message. On a phone the real class is
 * used and none of this is needed. (androidx.sqlite's SQLiteException is a typealias of this class.)
 */
public class SQLException extends RuntimeException
{
    public SQLException()
    {
        super();
    }

    public SQLException(String error)
    {
        super(error);
    }

    public SQLException(String error, Throwable cause)
    {
        super(error, cause);
    }
}
