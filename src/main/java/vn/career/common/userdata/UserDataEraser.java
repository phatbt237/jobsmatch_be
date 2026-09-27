package vn.career.common.userdata;

import java.util.UUID;

/**
 * Implemented by every module that stores personal data. Account deletion calls all of them, so the auth module
 * does not need to know about the other modules. Implementations run inside the deletion transaction.
 */
public interface UserDataEraser {

    /** Deletes or anonymises everything this module holds about the user. */
    void eraseUserData(UUID userId);
}
