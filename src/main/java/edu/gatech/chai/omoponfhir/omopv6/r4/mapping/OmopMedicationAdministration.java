package edu.gatech.chai.omoponfhir.omopv6.r4.mapping;

import ca.uhn.fhir.rest.param.*;
import edu.gatech.chai.omoponfhir.omopv6.r4.provider.*;
import edu.gatech.chai.omoponfhir.omopv6.r4.utilities.CodeableConceptUtil;
import edu.gatech.chai.omoponfhir.omopv6.r4.utilities.TerminologyServiceClient;
import edu.gatech.chai.omoponfhir.omopv6.r4.utilities.ThrowFHIRExceptions;
import edu.gatech.chai.omopv6.dba.service.*;
import edu.gatech.chai.omopv6.model.entity.*;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import java.util.*;

public class OmopMedicationAdministration extends BaseOmopResource<MedicationAdministration, DrugExposure, DrugExposureService>
            implements IResourceMapping<MedicationAdministration, DrugExposure> {
    private static final Logger logger = LoggerFactory.getLogger(OmopMedicationAdministration.class);

    private static OmopMedicationAdministration omopMedicationAdministration = new OmopMedicationAdministration();
    private VisitOccurrenceService visitOccurrenceService;
    private ConceptService conceptService;
    private ProviderService providerService;
    private FPersonService fPersonService;

    public OmopMedicationAdministration(WebApplicationContext context) {
        super(context, DrugExposure.class, DrugExposureService.class, MedicationAdministrationResourceProvider.getType());
        initialize(context);
    }

    public OmopMedicationAdministration() {
        super(ContextLoaderListener.getCurrentWebApplicationContext(), DrugExposure.class, DrugExposureService.class,
                MedicationAdministrationResourceProvider.getType());
        initialize(ContextLoaderListener.getCurrentWebApplicationContext());
    }

    private void initialize(WebApplicationContext context) {
        visitOccurrenceService = context.getBean(VisitOccurrenceService.class);
        conceptService = context.getBean(ConceptService.class);
        providerService = context.getBean(ProviderService.class);
        fPersonService = context.getBean(FPersonService.class);

        // Get count and put it in the counts.
        getSize();
    }

    public static OmopMedicationAdministration getInstance() {
        return OmopMedicationAdministration.omopMedicationAdministration;
    }

    @Override
    public Long toDbase(MedicationAdministration fhirResource, IdType fhirId) throws FHIRException {
        Long omopId = null;
        if (fhirId != null) {
            // Update
            Long fhirIdLong = fhirId.getIdPartAsLong();
            omopId = IdMapping.getOMOPfromFHIR(fhirIdLong, MedicationAdministrationResourceProvider.getType());
        }

        DrugExposure drugExposure = constructOmop(omopId, fhirResource);

        Long retOmopId = null;
        if (omopId == null) {
            retOmopId = getMyOmopService().create(drugExposure).getId();
        } else {
            retOmopId = getMyOmopService().update(drugExposure).getId();
        }

        return IdMapping.getFHIRfromOMOP(retOmopId, MedicationAdministrationResourceProvider.getType());
    }

    @Override
    public MedicationAdministration constructFHIR(Long fhirId, DrugExposure entity) {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setId(new IdType(fhirId));

        // status is required field in FHIR medicationAdministration.
        // However, we do not have a field in OMOP.
        // We will use stop_reason field to see if there is any data in there.
        // If we have data there, we set the status stopped. Otherwise, active.
        // We may need to use reasonNotTaken. But, we don't have a code for
        // that.
        // We will use note to put the reason if exists.
        if (entity.getStopReason() != null) {
            medicationAdministration.setStatus("STOPPED");
            Annotation annotation = new Annotation();
            annotation.setText(entity.getStopReason());
            medicationAdministration.addNote(annotation);
        } else {
            medicationAdministration.setStatus("ACTIVE");
        }

        FPerson fPerson = entity.getFPerson();
        if (fPerson != null) {
            Long omopFpersonId = fPerson.getId();
            Long fhirPatientId = IdMapping.getFHIRfromOMOP(omopFpersonId,
                    MedicationAdministrationResourceProvider.getType());
            Reference subjectReference = new Reference(new IdType(PatientResourceProvider.getType(), fhirPatientId));
            String familyName = fPerson.getFamilyName();
            String given1 = fPerson.getGivenName1();
            String given2 = fPerson.getGivenName2();
            String name = null;
            if (familyName != null && !familyName.isEmpty()) {
                name = familyName;
                if (given1 != null && !given1.isEmpty()) {
                    name = name.concat(", " + given1);
                    if (given2 != null && !given2.isEmpty()) {
                        name = name.concat(" " + given2);
                    }
                } else {
                    if (given2 != null && !given2.isEmpty()) {
                        name = name.concat(", " + given2);
                    }
                }
            } else {
                if (given1 != null && !given1.isEmpty()) {
                    name = given1;
                    if (given2 != null && given2.isEmpty()) {
                        name = name.concat(" " + given2);
                    }
                } else if (given2 != null && given2.isEmpty()) {
                    name = given2;
                }

            }
            if (name != null)
                subjectReference.setDisplay(name);
            medicationAdministration.setSubject(subjectReference);
        }

        // See if we have encounter associated with this medication statement.
        VisitOccurrence visitOccurrence = entity.getVisitOccurrence();
        if (visitOccurrence != null) {
            Long fhirEncounterId = IdMapping.getFHIRfromOMOP(visitOccurrence.getId(),
                    EncounterResourceProvider.getType());
            Reference reference = new Reference(new IdType(EncounterResourceProvider.getType(), fhirEncounterId));
            medicationAdministration.setContext(reference);
        }

        // Get medicationCodeableConcept
        Concept drugConcept = entity.getDrugConcept();
        CodeableConcept medication;
        try {
            medication = CodeableConceptUtil.getCodeableConceptFromOmopConcept(drugConcept);
        } catch (FHIRException e1) {
            e1.printStackTrace();
            return null;
        }

        medicationAdministration.setMedication(medication);

        // See if we can add ingredient version of this medication.
        // Concept ingredient = conceptService.getIngredient(drugConcept);
        // if (ingredient != null) {
        // CodeableConcept ingredientCodeableConcept;
        // try {
        // ingredientCodeableConcept =
        // CodeableConceptUtil.getCodeableConceptFromOmopConcept(ingredient);
        // if (!ingredientCodeableConcept.isEmpty()) {
        // // We have ingredient information. Add this to medicationAdministration.
        // // To do this, we need to add Medication resource to contained
        // section.
        // Medication medicationResource = new Medication();
        // medicationResource.setCode(medication);
        // MedicationIngredientComponent medIngredientComponent = new
        // MedicationIngredientComponent();
        // medIngredientComponent.setItem(ingredientCodeableConcept);
        // medicationResource.addIngredient(medIngredientComponent);
        // medicationResource.setId("med1");
        // medicationAdministration.addContained(medicationResource);
        // medicationAdministration.setMedication(new Reference("#med1"));
        // }
        // } catch (FHIRException e) {
        // e.printStackTrace();
        // return null;
        // }
        // } else {
        // medicationAdministration.setMedication(medication);
        // }

        // Get effectivePeriod
        Period period = new Period();
        Date startDate = entity.getDrugExposureStartDate();
        if (startDate != null) {
            period.setStart(startDate);
        }

        Date endDate = entity.getDrugExposureEndDate();
        if (endDate != null) {
            period.setEnd(endDate);
        }

        if (!period.isEmpty()) {
            medicationAdministration.setEffective(period);
        }

        // Get drug dose
//		Double effectiveDrugDose = entity.getEffectiveDrugDose();
        Double omopQuantity = entity.getQuantity();
        SimpleQuantity quantity = new SimpleQuantity();
//		if (effectiveDrugDose != null) {
//			quantity.setValue(effectiveDrugDose);
        if (omopQuantity != null) {
            quantity.setValue(omopQuantity);
        }

        String unitString = entity.getDoseUnitSourceValue();
        if (unitString != null && !unitString.isEmpty()) {
            try {
                Concept unitConcept = CodeableConceptUtil.getOmopConceptWithOmopCode(conceptService, unitString);
                if (unitConcept != null) {
                    String unitFhirUri = OmopCodeableConceptMapping
                            .fhirUriforOmopVocabulary(unitConcept.getVocabularyId());
                    if (!"None".equals(unitFhirUri)) {
                        String unitDisplay = unitConcept.getConceptName();
                        String unitCode = unitConcept.getConceptCode();
                        quantity.setUnit(unitDisplay);
                        quantity.setSystem(unitFhirUri);
                        quantity.setCode(unitCode);
                    }
                } else {
                    quantity.setUnit(unitString);
                }
            } catch (FHIRException e) {
                // We have null vocabulary id in the unit concept.
                // Throw error and move on.
                e.printStackTrace();
            }
        }

        MedicationAdministration.MedicationAdministrationDosageComponent dosage = new MedicationAdministration.MedicationAdministrationDosageComponent();
        if (!quantity.isEmpty()) {
            dosage.setDose(quantity);
        }

        Concept routeConcept = entity.getRouteConcept();
        if (routeConcept != null) {
            try {
                String myUri = fhirOmopVocabularyMap.getFhirSystemNameFromOmopVocabulary(routeConcept.getVocabularyId());
                if (!"None".equals(myUri)) {
                    CodeableConcept routeCodeableConcept = new CodeableConcept();
                    Coding routeCoding = new Coding();
                    routeCoding.setSystem(myUri);
                    routeCoding.setCode(routeConcept.getConceptCode());
                    routeCoding.setDisplay(routeConcept.getConceptName());

                    routeCodeableConcept.addCoding(routeCoding);
                    dosage.setRoute(routeCodeableConcept);
                }
            } catch (FHIRException e) {
                e.printStackTrace();
            }
        }

        String sig = entity.getSig();
        if (sig != null && !sig.isEmpty()) {
            dosage.setText(sig);
        }


        if (!dosage.isEmpty())
            medicationAdministration.setDosage(dosage);

        // Get information source
//        Provider provider = entity.getProvider();
//        if (provider != null) {
//            Long fhirPractitionerId = IdMapping.getFHIRfromOMOP(provider.getId(),
//                    PractitionerResourceProvider.getType());
//            Reference infoSourceReference = new Reference(
//                    new IdType(PractitionerResourceProvider.getType(), fhirPractitionerId));
//            if (provider.getProviderName() != null && !provider.getProviderName().isEmpty())
//                infoSourceReference.setDisplay(provider.getProviderName());
//            medicationAdministration.setInformationSource(infoSourceReference);
//        }

        // If OMOP medication type has the following prescription type, we set
        // basedOn reference to the prescription.
//        if (entity.getDrugTypeConcept() != null) {
//            if (entity.getDrugTypeConcept().getId() == OmopMedicationRequest.MEDICATIONREQUEST_CONCEPT_TYPE_ID) {
//                IdType referenceIdType = new IdType(MedicationRequestResourceProvider.getType(),
//                        IdMapping.getFHIRfromOMOP(entity.getId(), MedicationRequestResourceProvider.getType()));
//                Reference basedOnReference = new Reference(referenceIdType);
//                medicationAdministration.addBasedOn(basedOnReference);
//            } else if (entity.getDrugTypeConcept().getId() == 38000179L
//                    || entity.getDrugTypeConcept().getId() == 38000180L
//                    || entity.getDrugTypeConcept().getId() == 43542356L
//                    || entity.getDrugTypeConcept().getId() == 43542357L
//                    || entity.getDrugTypeConcept().getId() == 43542358L
//                    || entity.getDrugTypeConcept().getId() == 581373L) {
//                // This is administration related...
//                // TODO: add partOf to MedicationAdministration reference after we implement
//                // Medication Administration
//            } else if (entity.getDrugTypeConcept().getId() == 38000175L
//                    || entity.getDrugTypeConcept().getId() == 38000176L
//                    || entity.getDrugTypeConcept().getId() == 581452L) {
//                // TODO: add partOf to MedicationDispense reference.
////				IdType referenceIdType = new IdType("MedicationDispense", IdMapping.getFHIRfromOMOP(entity.getId(), "MedicationDispense"));
////				medicationAdministration.addPartOf(new Reference(referenceIdType));
//            }
//
//        }

        // If OMOP medicaiton type has the administration or dispense, we set
        // partOf reference to this.

        return medicationAdministration;
    }

    @Override
    public List<ParameterWrapper> mapParameter(String parameter, Object value, boolean or) {
        List<ParameterWrapper> mapList = new ArrayList<ParameterWrapper>();
        ParameterWrapper paramWrapper = new ParameterWrapper();
        if (or)
            paramWrapper.setUpperRelationship("or");
        else
            paramWrapper.setUpperRelationship("and");

        switch (parameter) {
            case MedicationAdministration.SP_RES_ID:
                String medicationAdministrationId = ((TokenParam) value).getValue();
                paramWrapper.setParameterType("Long");
                paramWrapper.setParameters(Arrays.asList("id"));
                paramWrapper.setOperators(Arrays.asList("="));
                paramWrapper.setValues(Arrays.asList(medicationAdministrationId));
                paramWrapper.setRelationship("or");
                mapList.add(paramWrapper);
                break;
            case MedicationAdministration.SP_CODE:
                TokenParam theCode = (TokenParam) value;
                String system = theCode.getSystem();
                String code = theCode.getValue();
                String omopVocabulary = "None";

                if ((system == null || system.isEmpty()) && (code == null || code.isEmpty()))
                    break;

                if (theCode.getModifier() != null && theCode.getModifier().compareTo(TokenParamModifier.IN) == 0) {
                    // code has URI for the valueset search.
                    TerminologyServiceClient terminologyService = TerminologyServiceClient.getInstance();
                    Map<String, List<ValueSet.ConceptSetComponent>> theIncExcl = terminologyService.getValueSetByUrl(code);

                    List<ValueSet.ConceptSetComponent> includes = theIncExcl.get("include");
                    List<String> values = new ArrayList<String>();
                    for (ValueSet.ConceptSetComponent include : includes) {
                        // We need to loop
                        ParameterWrapper myParamWrapper = new ParameterWrapper();
                        myParamWrapper.setParameterType("Code:In");
                        myParamWrapper.setParameters(Arrays.asList("drugConcept.vocabularyId", "drugConcept.conceptCode"));
                        myParamWrapper.setOperators(Arrays.asList("=", "in"));

                        String valueSetSystem = include.getSystem();
                        try {
                            omopVocabulary = OmopCodeableConceptMapping.omopVocabularyforFhirUri(valueSetSystem);
                        } catch (FHIRException e) {
                            e.printStackTrace();
                        }
                        if ("None".equals(omopVocabulary)) {
                            ThrowFHIRExceptions.unprocessableEntityException(
                                    "We don't understand the system, " + valueSetSystem + " in code:in valueset");
                        }
                        values.add(valueSetSystem);

                        List<ValueSet.ConceptReferenceComponent> concepts = include.getConcept();
                        for (ValueSet.ConceptReferenceComponent concept : concepts) {
                            String valueSetCode = concept.getCode();
                            values.add(valueSetCode);
                        }
                        myParamWrapper.setValues(values);
                        myParamWrapper.setUpperRelationship("or");
                        mapList.add(myParamWrapper);
                    }

                    List<ValueSet.ConceptSetComponent> excludes = theIncExcl.get("exclude");
                    for (ValueSet.ConceptSetComponent exclude : excludes) {
                        // We need to loop
                        ParameterWrapper myParamWrapper = new ParameterWrapper();
                        myParamWrapper.setParameterType("Code:In");
                        myParamWrapper.setParameters(Arrays.asList("drugConcept.vocabularyId", "drugConcept.conceptCode"));
                        myParamWrapper.setOperators(Arrays.asList("=", "out"));

                        String valueSetSystem = exclude.getSystem();
                        try {
                            omopVocabulary = OmopCodeableConceptMapping.omopVocabularyforFhirUri(valueSetSystem);
                        } catch (FHIRException e) {
                            e.printStackTrace();
                        }
                        if ("None".equals(omopVocabulary)) {
                            ThrowFHIRExceptions.unprocessableEntityException(
                                    "We don't understand the system, " + valueSetSystem + " in code:in valueset");
                        }
                        values.add(valueSetSystem);

                        List<ValueSet.ConceptReferenceComponent> concepts = exclude.getConcept();
                        for (ValueSet.ConceptReferenceComponent concept : concepts) {
                            String valueSetCode = concept.getCode();
                            values.add(valueSetCode);
                        }
                        myParamWrapper.setValues(values);
                        myParamWrapper.setUpperRelationship("and");
                        mapList.add(myParamWrapper);
                    }
                } else {
                    if (system != null && !system.isEmpty()) {
                        try {
//						omopVocabulary = OmopCodeableConceptMapping.omopVocabularyforFhirUri(system);
                            omopVocabulary = fhirOmopVocabularyMap.getOmopVocabularyFromFhirSystemName(system);
                        } catch (FHIRException e) {
                            e.printStackTrace();
                        }
                    }

                    paramWrapper.setParameterType("String");
                    if ("None".equals(omopVocabulary) && code != null && !code.isEmpty()) {
                        paramWrapper.setParameters(Arrays.asList("drugConcept.conceptCode"));
                        paramWrapper.setOperators(Arrays.asList("like"));
                        paramWrapper.setValues(Arrays.asList(code));
                    } else if (!"None".equals(omopVocabulary) && (code == null || code.isEmpty())) {
                        paramWrapper.setParameters(Arrays.asList("drugConcept.vocabularyId"));
                        paramWrapper.setOperators(Arrays.asList("like"));
                        paramWrapper.setValues(Arrays.asList(omopVocabulary));
                    } else {
                        paramWrapper.setParameters(Arrays.asList("drugConcept.vocabularyId", "drugConcept.conceptCode"));
                        paramWrapper.setOperators(Arrays.asList("like", "like"));
                        paramWrapper.setValues(Arrays.asList(omopVocabulary, code));
                    }
                    paramWrapper.setRelationship("and");
                    mapList.add(paramWrapper);
                }
                break;
            case MedicationAdministration.SP_CONTEXT:
                Long fhirEncounterId = ((ReferenceParam) value).getIdPartAsLong();
                Long omopVisitOccurrenceId = IdMapping.getOMOPfromFHIR(fhirEncounterId,
                        EncounterResourceProvider.getType());
                // String resourceName = ((ReferenceParam) value).getResourceType();

                // We support Encounter so the resource type should be Encounter.
                if (omopVisitOccurrenceId != null) {
                    paramWrapper.setParameterType("Long");
                    paramWrapper.setParameters(Arrays.asList("visitOccurrence.id"));
                    paramWrapper.setOperators(Arrays.asList("="));
                    paramWrapper.setValues(Arrays.asList(String.valueOf(omopVisitOccurrenceId)));
                    paramWrapper.setRelationship("or");
                    mapList.add(paramWrapper);
                }
                break;
            case MedicationAdministration.SP_EFFECTIVE_TIME:
                DateParam effectiveDateParam = ((DateParam) value);
                ParamPrefixEnum apiOperator = effectiveDateParam.getPrefix();
                String sqlOperator = null;
                if (apiOperator.equals(ParamPrefixEnum.GREATERTHAN)) {
                    sqlOperator = ">";
                } else if (apiOperator.equals(ParamPrefixEnum.GREATERTHAN_OR_EQUALS)) {
                    sqlOperator = ">=";
                } else if (apiOperator.equals(ParamPrefixEnum.LESSTHAN)) {
                    sqlOperator = "<";
                } else if (apiOperator.equals(ParamPrefixEnum.LESSTHAN_OR_EQUALS)) {
                    sqlOperator = "<=";
                } else if (apiOperator.equals(ParamPrefixEnum.NOT_EQUAL)) {
                    sqlOperator = "!=";
                } else {
                    sqlOperator = "=";
                }
                Date effectiveDate = effectiveDateParam.getValue();

                paramWrapper.setParameterType("Date");
                paramWrapper.setParameters(Arrays.asList("drugExposureStartDate"));
                paramWrapper.setOperators(Arrays.asList(sqlOperator));
                paramWrapper.setValues(Arrays.asList(String.valueOf(effectiveDate.getTime())));
                paramWrapper.setRelationship("or");
                mapList.add(paramWrapper);
                break;
            case "Patient:" + Patient.SP_RES_ID:
                addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
                break;
            case "Patient:" + Patient.SP_NAME:
                addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
                break;
            case "Patient:" + Patient.SP_IDENTIFIER:
                addParamlistForPatientIDName(parameter, (String) value, paramWrapper, mapList);
                break;
//		case MedicationStatement.SP_PATIENT:
//			ReferenceParam patientReference = ((ReferenceParam) value);
//			Long fhirPatientId = patientReference.getIdPartAsLong();
//			Long omopPersonId = IdMapping.getOMOPfromFHIR(fhirPatientId, PatientResourceProvider.getType());
//
//			String omopPersonIdString = String.valueOf(omopPersonId);
//
//			paramWrapper.setParameterType("Long");
//			paramWrapper.setParameters(Arrays.asList("fPerson.id"));
//			paramWrapper.setOperators(Arrays.asList("="));
//			paramWrapper.setValues(Arrays.asList(omopPersonIdString));
//			paramWrapper.setRelationship("or");
//			mapList.add(paramWrapper);
//			break;
//            case MedicationAdministration.SP_SOURCE:
//                ReferenceParam sourceReference = ((ReferenceParam) value);
//                String sourceReferenceId = String.valueOf(sourceReference.getIdPartAsLong());
//
//                paramWrapper.setParameterType("Long");
//                paramWrapper.setParameters(Arrays.asList("provider.id"));
//                paramWrapper.setOperators(Arrays.asList("="));
//                paramWrapper.setValues(Arrays.asList(sourceReferenceId));
//                paramWrapper.setRelationship("or");
//                mapList.add(paramWrapper);
//                break;
            default:
                mapList = null;
        }

        return mapList;
    }

    @Override
    public DrugExposure constructOmop(Long omopId, MedicationAdministration fhirResource) {
        //todo: implement fhir to omop conversion
        return null;
    }

}

