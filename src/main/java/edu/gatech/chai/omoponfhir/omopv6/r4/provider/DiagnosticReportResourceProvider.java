package edu.gatech.chai.omoponfhir.omopv6.r4.provider;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.*;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.*;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import edu.gatech.chai.omoponfhir.omopv6.r4.mapping.OmopDiagnosticReport;
import edu.gatech.chai.omoponfhir.omopv6.r4.mapping.OmopObservation;
import edu.gatech.chai.omoponfhir.omopv6.r4.utilities.ThrowFHIRExceptions;
import edu.gatech.chai.omopv6.dba.service.ParameterWrapper;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DiagnosticReportResourceProvider implements IResourceProvider {
    private WebApplicationContext myAppCtx;
    private String myDbType;
    private OmopDiagnosticReport myMapper;
    private int preferredPageSize = 30;

    public DiagnosticReportResourceProvider() {
        myAppCtx = ContextLoaderListener.getCurrentWebApplicationContext();
        myDbType = myAppCtx.getServletContext().getInitParameter("backendDbType");
        if (myDbType.equalsIgnoreCase("omopv5") == true) {
            myMapper = new OmopDiagnosticReport(myAppCtx);
        } else {
            myMapper = new OmopDiagnosticReport(myAppCtx);
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
        return "DiagnosticReport";
    }

    public OmopDiagnosticReport getMyMapper() {
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
     * The "@Create" annotation indicates that this method implements "create=type", which adds a
     * new instance of a resource to the server.
     */
    @Create()
    public MethodOutcome createPatient(@ResourceParam DiagnosticReport theObservation) {
        validateResource(theObservation);

        Long id = null;
        try {
            id = getMyMapper().toDbase(theObservation, null);
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
    public void deleteDiagnosticReport(@IdParam IdType theId) {
        if (getMyMapper().removeByFhirId(theId) <= 0) {
            throw new ResourceNotFoundException(theId);
        }
    }

    @Search()
    public IBundleProvider findDiagnosticReportsById(
            @RequiredParam(name=Observation.SP_RES_ID) TokenParam theObservationId,
            @Sort SortSpec theSort,

            @IncludeParam(allow={"DiagnosticReport:based-on", "DiagnosticReport:context",
                    "DiagnosticReport:device", "DiagnosticReport:encounter", "DiagnosticReport:patient",
                    "DiagnosticReport:performer", "DiagnosticReport:related-target",
                    "DiagnosticReport:specimen", "DiagnosticReport:subject"})
            final Set<Include> theIncludes,

            @IncludeParam(reverse=true)
            final Set<Include> theReverseIncludes
    ) {
        List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper> ();

        if (theObservationId != null) {
            paramList.addAll(getMyMapper().mapParameter (Observation.SP_RES_ID, theObservationId, false));
        }

        String orderParams = getMyMapper().constructOrderParams(theSort);

        DiagnosticReportResourceProvider.MyBundleProvider myBundleProvider = new DiagnosticReportResourceProvider.MyBundleProvider(paramList, theIncludes, theReverseIncludes);
        myBundleProvider.setTotalSize(getTotalSize(paramList));
        myBundleProvider.setPreferredPageSize(preferredPageSize);
        myBundleProvider.setOrderParams(orderParams);
        return myBundleProvider;
    }

    @Search()
    public IBundleProvider findDiagnosticReportsByParams(
            @OptionalParam(name=Observation.SP_CODE) TokenOrListParam theOrCodes,
            @OptionalParam(name=Observation.SP_DATE) DateRangeParam theRangeDate,
            @OptionalParam(name=Observation.SP_PATIENT, chainWhitelist={"", Patient.SP_NAME, Patient.SP_IDENTIFIER}) ReferenceParam thePatient,
            @OptionalParam(name=Observation.SP_SUBJECT, chainWhitelist={"", Patient.SP_NAME, Patient.SP_IDENTIFIER}) ReferenceParam theSubject,
            @Sort SortSpec theSort,

            @IncludeParam(allow={"DiagnosticReport:based-on", "DiagnosticReport:context",
                    "DiagnosticReport:device", "DiagnosticReport:encounter", "DiagnosticReport:patient",
                    "DiagnosticReport:performer", "DiagnosticReport:related-target",
                    "DiagnosticReport:specimen", "DiagnosticReport:subject"})
            final Set<Include> theIncludes,

            @IncludeParam(reverse=true)
            final Set<Include> theReverseIncludes
    ) {
        List<ParameterWrapper> paramList = new ArrayList<ParameterWrapper> ();

        if (theOrCodes != null) {
            List<TokenParam> codes = theOrCodes.getValuesAsQueryTokens();
            boolean orValue = true;
            if (codes.size() <= 1)
                orValue = false;
            for (TokenParam code : codes) {
                paramList.addAll(getMyMapper().mapParameter(Observation.SP_CODE, code, orValue));
            }
        }

        if (theRangeDate != null) {
            paramList.addAll(getMyMapper().mapParameter(Observation.SP_DATE, theRangeDate, false));
        }

        // With OMOP, we only support subject to be patient.
        // If the subject has only ID part, we assume that is patient.
        if (theSubject != null) {
            if (theSubject.getResourceType() != null &&
                    theSubject.getResourceType().equals(PatientResourceProvider.getType())) {
                thePatient = theSubject;
            } else {
                // If resource is null, we assume Patient.
                if (theSubject.getResourceType() == null) {
                    thePatient = theSubject;
                } else {
                    ThrowFHIRExceptions.unprocessableEntityException("subject search allows Only Patient Resource, but provided "+theSubject.getResourceType());
                }
            }
        }

        if (thePatient != null) {
            String patientChain = thePatient.getChain();
            if (patientChain != null) {
                if (Patient.SP_NAME.equals(patientChain)) {
                    String thePatientName = thePatient.getValue();
                    paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_NAME, thePatientName, false));
                } else if (Patient.SP_IDENTIFIER.equals(patientChain)) {
                    paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_IDENTIFIER, thePatient.getValue(), false));
                } else if ("".equals(patientChain)) {
                    paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_RES_ID, thePatient.getValue(), false));
                }
            } else {
                paramList.addAll(getMyMapper().mapParameter ("Patient:"+Patient.SP_RES_ID, thePatient.getIdPart(), false));
            }
        }

        String orderParams = getMyMapper().constructOrderParams(theSort);

        DiagnosticReportResourceProvider.MyBundleProvider myBundleProvider = new DiagnosticReportResourceProvider.MyBundleProvider(paramList, theIncludes, theReverseIncludes);
        myBundleProvider.setTotalSize(getTotalSize(paramList));
        myBundleProvider.setPreferredPageSize(preferredPageSize);
        myBundleProvider.setOrderParams(orderParams);
        return myBundleProvider;
    }

    /**
     * This is the "read" operation. The "@Read" annotation indicates that this method supports the read and/or vread operation.
     * <p>
     * Read operations take a single parameter annotated with the {@link IdParam} paramater, and should return a single resource instance.
     * </p>
     *
     * @param theId
     *            The read operation takes one parameter, which must be of type IdDt and must be annotated with the "@Read.IdParam" annotation.
     * @return Returns a resource matching this identifier, or null if none exists.
     */
    @Read()
    public DiagnosticReport readDiagnosticReport(@IdParam IdType theId) {
        DiagnosticReport retval = (DiagnosticReport) getMyMapper().toFHIR(theId);
        if (retval == null) {
            throw new ResourceNotFoundException(theId);
        }

        return retval;
    }

    /**
     * The "@Update" annotation indicates that this method supports replacing an existing
     * resource (by ID) with a new instance of that resource.
     *
     * @param theId
     *            This is the ID of the patient to update
     * @param thePatient
     *            This is the actual resource to save
     * @return This method returns a "MethodOutcome"
     */
    @Update()
    public MethodOutcome updateDiagnosticReport(@IdParam IdType theId, @ResourceParam DiagnosticReport theObservation) {
        validateResource(theObservation);

        Long fhirId=null;
        try {
            fhirId = getMyMapper().toDbase(theObservation, theId);
        } catch (FHIRException e) {
            e.printStackTrace();
        }

        if (fhirId == null) {
            throw new ResourceNotFoundException(theId);
        }

        return new MethodOutcome();
    }

    // TODO: Add more validation code here.
    private void validateResource(DiagnosticReport theObservation) {
        OperationOutcome outcome = new OperationOutcome();
        CodeableConcept detailCode = new CodeableConcept();
        if (theObservation.getCode().isEmpty()) {
            detailCode.setText("No code is provided.");
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(detailCode);
            throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
        }

        Reference subjectReference = theObservation.getSubject();
        if (subjectReference == null || subjectReference.isEmpty()) {
            detailCode.setText("Subject cannot be empty for OmopOnFHIR");
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(detailCode);
            throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
        }

        String subjectResource = subjectReference.getReferenceElement().getResourceType();
        if (!subjectResource.contentEquals("Patient")) {
            detailCode.setText("Subject ("+subjectResource+") must be Patient resource for OmopOnFHIR");
            outcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.FATAL).setDetails(detailCode);
            throw new UnprocessableEntityException(FhirContext.forDstu3(), outcome);
        }
    }

    @Override
    public Class<DiagnosticReport> getResourceType() {
        return DiagnosticReport.class;
    }

    class MyBundleProvider extends OmopFhirBundleProvider implements IBundleProvider {
        Set<Include> theIncludes;
        Set<Include> theReverseIncludes;

        public MyBundleProvider(List<ParameterWrapper> paramList, Set<Include> theIncludes, Set<Include>theReverseIncludes) {
            super(paramList);
            setPreferredPageSize (preferredPageSize);
            this.theIncludes = theIncludes;
            this.theReverseIncludes = theReverseIncludes;
        }

        @Override
        public List<IBaseResource> getResources(int fromIndex, int toIndex) {
            List<IBaseResource> retv = new ArrayList<IBaseResource>();

            // _Include
            List<String> includes = new ArrayList<String>();

            if (theIncludes.contains(new Include("DiagnosticReport:based-on"))) {
                includes.add("DiagnosticReport:based-on");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:context"))) {
                includes.add("DiagnosticReport:context");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:device"))) {
                includes.add("DiagnosticReport:device");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:encounter"))) {
                includes.add("DiagnosticReport:encounter");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:patient"))) {
                includes.add("DiagnosticReport:patient");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:performer"))) {
                includes.add("DiagnosticReport:performer");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:related-target"))) {
                includes.add("DiagnosticReport:related-target");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:specimen"))) {
                includes.add("DiagnosticReport:specimen");
            }

            if (theIncludes.contains(new Include("DiagnosticReport:subject"))) {
                includes.add("DiagnosticReport:subject");
            }

            if (paramList.size() == 0) {
                getMyMapper().searchWithoutParams(fromIndex, toIndex, retv, includes, orderParams);
            } else {
                getMyMapper().searchWithParams(fromIndex, toIndex, paramList, retv, includes, orderParams);
            }

            return retv;
        }
    }

}
