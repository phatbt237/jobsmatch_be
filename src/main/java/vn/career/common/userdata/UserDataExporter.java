package vn.career.common.userdata;

import java.util.UUID;

/** Implemented by every module that stores personal data, to contribute one section of the personal data export. */
public interface UserDataExporter {

    /** Key of this module's section in the export JSON, for example "surveyAttempts". */
    String section();

    /** Everything this module holds about the user, as JSON-serialisable data. */
    Object exportUserData(UUID userId);
}
