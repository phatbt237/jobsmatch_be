package vn.career.survey.infrastructure;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vn.career.survey.api.dto.AdminOptionResponse;
import vn.career.survey.api.dto.AdminQuestionResponse;
import vn.career.survey.api.dto.AdminSectionResponse;
import vn.career.survey.api.dto.AdminSurveyResponse;
import vn.career.survey.api.dto.ConstraintsResponse;
import vn.career.survey.api.dto.DimensionResponse;
import vn.career.survey.api.dto.OptionResponse;
import vn.career.survey.api.dto.QuestionResponse;
import vn.career.survey.api.dto.SectionResponse;
import vn.career.survey.domain.Dimension;
import vn.career.survey.domain.Question;
import vn.career.survey.domain.QuestionOption;
import vn.career.survey.domain.StudentConstraints;
import vn.career.survey.domain.Survey;
import vn.career.survey.domain.SurveySection;

/**
 * Student-facing mappings deliberately have no field for dimension, weight, reverse flag, attention flag,
 * expected value, option score or correctness, so those can never leak through them.
 */
@Mapper
public interface SurveyMapper {

    SectionResponse toStudentSection(SurveySection section);

    QuestionResponse toStudentQuestion(Question question);

    OptionResponse toStudentOption(QuestionOption option);

    AdminSurveyResponse toAdminSurvey(Survey survey);

    AdminSectionResponse toAdminSection(SurveySection section);

    @Mapping(target = "sectionId", source = "section.id")
    AdminQuestionResponse toAdminQuestion(Question question);

    AdminOptionResponse toAdminOption(QuestionOption option);

    DimensionResponse toDimensionResponse(Dimension dimension);

    ConstraintsResponse toConstraintsResponse(StudentConstraints constraints);
}
