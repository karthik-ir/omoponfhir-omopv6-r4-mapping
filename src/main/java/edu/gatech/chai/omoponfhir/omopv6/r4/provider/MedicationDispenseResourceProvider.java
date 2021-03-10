package edu.gatech.chai.omoponfhir.omopv6.r4.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import edu.gatech.chai.omoponfhir.omopv6.r4.mapping.OmopMedicationAdministration;
import edu.gatech.chai.omoponfhir.omopv6.r4.mapping.OmopMedicationDispense;
import edu.gatech.chai.omopv6.dba.service.ParameterWrapper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

public class MedicationDispenseResourceProvider implements IResourceProvider {

    private WebApplicationContext myAppCtx;
    private String myDbType;
    private OmopMedicationDispense myMapper;
    private int preferredPageSize = 30;

    public MedicationDispenseResourceProvider() {
        myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
        myDbType = myAppCtx.getServletContext().getInitParameter("backendDbType");
        if (myDbType.equalsIgnoreCase("omopv5") == true) {
            myMapper = new OmopMedicationDispense(myAppCtx);
        } else {
            myMapper = new OmopMedicationDispense(myAppCtx);
        }

        String pageSizeStr = myAppCtx.getServletContext().getInitParameter("preferredPageSize");
        if (pageSizeStr != null && pageSizeStr.isEmpty() == false) {
            int pageSize = Integer.parseInt(pageSizeStr);
            if (pageSize > 0) {
                preferredPageSize = pageSize;
            }
        }
    }

    public static String getType() {
        return "MedicationDispense";
    }

    public OmopMedicationDispense getMyMapper() {
        return myMapper;

    }

    private Integer getTotalSize(List<ParameterWrapper> paramList) {
        final Long totalSize;
        if (paramList.size() == 0) {
            totalSize = getMyMapper().getSize();
        } else {
            totalSize = getMyMapper().getSize(paramList);
        }

        return totalSize.intValue();
    }

    /**
     * The "@Create" annotation indicates that this method implements "create=type",
     * which adds a new instance of a resource to the server.
     */
    @Create()
    public MethodOutcome createMedicationDispense(@ResourceParam MedicationDispense theMedicationDispense) {
        validateResource(theMedicationDispense);

        Long id = null;
        try {
            id = myMapper.toDbase(theMedicationDispense, null);
        } catch (FHIRException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (id == null) {
            OperationOutcome outcome = new OperationOutcome();
            CodeableConcept detailCode = new CodeableConcept();
            detailCode.setText("Failed to create entity.");
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(detailCode);
            throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
        }

        return new MethodOutcome(new IdDt(id));
    }

    @Delete()
    public void deleteMedicationDispense(@IdParam IdType theId) {
        if (myMapper.removeByFhirId(theId) <= 0) {
            throw new ResourceNotFoundException(theId);
        }
    }

    @Update()
    public MethodOutcome updateMedicationDispense(@IdParam IdType theId,
                                                   @ResourceParam MedicationDispense theMedicationDispense) {
        validateResource(theMedicationDispense);

        Long fhirId = null;
        try {
            fhirId = myMapper.toDbase(theMedicationDispense, theId);
        } catch (FHIRException e) {
            e.printStackTrace();
        }

        if (fhirId == null) {
            throw new ResourceNotFoundException(theId);
        }

        return new MethodOutcome();
    }

    @Read()
    public MedicationDispense readMedicationDispense(@IdParam IdType theId) {
        MedicationDispense retval = (MedicationDispense) myMapper.toFHIR(theId);
        if (retval == null) {
            throw new ResourceNotFoundException(theId);
        }

        return retval;
    }

    @Search()
    public IBundleProvider findMedicationDispenseById(
            @RequiredParam(name = MedicationDispense.SP_RES_ID) TokenParam theMedicationDispenseId) {
        List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

        if (theMedicationDispenseId != null) {
            paramList.addAll(myMapper.mapParameter(MedicationDispense.SP_RES_ID, theMedicationDispenseId, false));
        }

        MyBundleProvider myBundleProvider = new MyBundleProvider(paramList);
        myBundleProvider.setTotalSize(getTotalSize(paramList));
        myBundleProvider.setPreferredPageSize(preferredPageSize);
        return myBundleProvider;
    }

    @Search()
    public IBundleProvider findMedicationDispenseByParams(
            @OptionalParam(name = MedicationDispense.SP_CODE) TokenOrListParam theOrCodes,
            @OptionalParam(name = MedicationDispense.SP_CONTEXT) ReferenceParam theContext,
            @OptionalParam(name = MedicationDispense.SP_WHENHANDEDOVER) DateParam theDate,
            @OptionalParam(name = MedicationDispense.SP_PATIENT, chainWhitelist = { "", Patient.SP_NAME,
                    Patient.SP_IDENTIFIER }) ReferenceOrListParam thePatients,
            @OptionalParam(name = MedicationDispense.SP_SUBJECT, chainWhitelist = { "", Patient.SP_NAME,
                    Patient.SP_IDENTIFIER }) ReferenceOrListParam theSubjects) {
        List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper>();

        if (theOrCodes != null) {
            List<TokenParam> codes = theOrCodes.getValuesAsQueryTokens();

            if (codes.size() == 1) {
                TokenParam theCode = codes.get(0);
                if (theCode.getModifier() != null && theCode.getModifier().compareTo(TokenParamModifier.IN) == 0) {
                    // We have modifier to search data in certain code value set.
                    // With this modifier, the code is URI for value set.
                    String valueSetValue = theCode.getValue();
                    if (valueSetValue.split("?").length > 1) {
                        errorProcessing(
                                "code:in=" + valueSetValue + " is not supported. We only support simple value set URL");
                    }
                }
                paramList.addAll(myMapper.mapParameter(MedicationDispense.SP_CODE, theCode, false));

            } else {
                for (TokenParam code : codes) {
                    paramList.addAll(myMapper.mapParameter(MedicationDispense.SP_CODE, code, true));
                }
            }
        }
        if (theContext != null) {
            paramList.addAll(myMapper.mapParameter(MedicationDispense.SP_CONTEXT, theContext, false));
        }

        if (theDate != null) {
            paramList.addAll(myMapper.mapParameter(MedicationDispense.SP_WHENHANDEDOVER, theDate, false));
        }

        if (theSubjects != null) {
            List<ReferenceParam> subjects = theSubjects.getValuesAsQueryTokens();
            for (ReferenceParam subject : subjects) {
                if (!subject.getResourceType().equals(PatientResourceProvider.getType())) {
//					thePatient = theSubject;
//				} else {
                    errorProcessing("subject search allows Only Patient Resource.");
                }
            }

            thePatients = theSubjects;
        }

        if (thePatients != null) {
            List<ReferenceParam> patients = thePatients.getValuesAsQueryTokens();
            boolean or = false;
            if (patients.size() > 1) {
                or = true;
            }

            for (ReferenceParam patient : patients) {
                String patientChain = patient.getChain();
                if (patientChain != null) {
                    if (Patient.SP_NAME.equals(patientChain)) {
                        String thePatientName = patient.getValue();
                        paramList.addAll(myMapper.mapParameter("Patient:" + Patient.SP_NAME, thePatientName, or));
                    } else if (Patient.SP_IDENTIFIER.equals(patientChain)) {
                        paramList.addAll(myMapper.mapParameter("Patient:" + Patient.SP_IDENTIFIER, patient.getValue(), or));
                    } else if ("".equals(patientChain)) {
                        paramList.addAll(myMapper.mapParameter("Patient:" + Patient.SP_RES_ID, patient.getValue(), or));
                    }
                } else {
                    paramList.addAll(myMapper.mapParameter("Patient:" + Patient.SP_RES_ID, patient.getIdPart(), or));
                }
            }

        }

        MyBundleProvider myBundleProvider = new MyBundleProvider(paramList);
        myBundleProvider.setTotalSize(getTotalSize(paramList));
        myBundleProvider.setPreferredPageSize(preferredPageSize);
        return myBundleProvider;

    }

    @Override
    public Class<? extends IBaseResource> getResourceType() {
        return MedicationDispense.class;
    }

    private void errorProcessing(String msg) {
        OperationOutcome outcome = new OperationOutcome();
        CodeableConcept detailCode = new CodeableConcept();
        detailCode.setText(msg);
        outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(detailCode);
        throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
    }

    /**
     * This method just provides simple business validation for resources we are
     * storing.
     *
     * @param theMedication The medication dispense to validate
     */
    private void validateResource(MedicationDispense theMedication) {
        /*
         * Our server will have a rule that patients must have a family name or we will
         * reject them
         */
//		if (thePatient.getNameFirstRep().getFamily().isEmpty()) {
//			OperationOutcome outcome = new OperationOutcome();
//			CodeableConcept detailCode = new CodeableConcept();
//			detailCode.setText("No family name provided, Patient resources must have at least one family name.");
//			outcome.addIssue().setSeverity(IssueSeverity.FATAL).setDetails(detailCode);
//			throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
//		}
    }

    class MyBundleProvider extends OmopFhirBundleProvider implements IBundleProvider {

        public MyBundleProvider(List<ParameterWrapper> paramList) {
            super(paramList);
        }

        @Override
        public List<IBaseResource> getResources(int fromIndex, int toIndex) {
            List<IBaseResource> retv = new ArrayList<IBaseResource>();

            // _Include
            List<String> includes = new ArrayList<String>();

            if (paramList.size() == 0) {
                myMapper.searchWithoutParams(fromIndex, toIndex, retv, includes, null);
            } else {
                myMapper.searchWithParams(fromIndex, toIndex, paramList, retv, includes, null);
            }

            return retv;
        }
    }



}

