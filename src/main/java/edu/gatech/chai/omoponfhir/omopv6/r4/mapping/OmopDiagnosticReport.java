package edu.gatech.chai.omoponfhir.omopv6.r4.mapping;

import ca.uhn.fhir.rest.api.SortSpec;
import edu.gatech.chai.omoponfhir.omopv6.r4.provider.ObservationResourceProvider;
import edu.gatech.chai.omopv6.dba.service.FObservationViewService;
import edu.gatech.chai.omopv6.dba.service.ParameterWrapper;
import edu.gatech.chai.omopv6.model.entity.FObservationView;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

public class OmopDiagnosticReport extends BaseOmopResource<DiagnosticReport, FObservationView, FObservationViewService>
        implements IResourceMapping<DiagnosticReport, FObservationView> {

    OmopObservation omopObservation;

    public OmopDiagnosticReport(WebApplicationContext context) {
        super(context, FObservationView.class, FObservationViewService.class, ObservationResourceProvider.getType());
        this.omopObservation = new OmopObservation(context);
    }

    public OmopDiagnosticReport(WebApplicationContext context, Class<FObservationView> entityClass, Class<FObservationViewService> serviceClass, String fhirResourceType) {
        super(context, entityClass, serviceClass, fhirResourceType);
        this.omopObservation = new OmopObservation(context);
    }

    public OmopDiagnosticReport() {
        super(ContextLoaderListener.getCurrentWebApplicationContext(), FObservationView.class,
                FObservationViewService.class, ObservationResourceProvider.getType());
        this.omopObservation = new OmopObservation();
    }

    @Override
    public DiagnosticReport constructFHIR(Long fhirId, FObservationView entity) {
        Observation observation = this.omopObservation.constructFHIR(fhirId, entity);
        return null;
    }

    @Override
    public Long removeByFhirId(IdType fhirId) {
        return this.omopObservation.removeByFhirId(fhirId);
    }

    @Override
    public String constructOrderParams(SortSpec theSort) {
        return this.omopObservation.constructOrderParams(theSort);
    }

    @Override
    public Long toDbase(DiagnosticReport fhirResource, IdType fhirId) throws FHIRException {
        return null;
    }

    @Override
    public Long getSize(List<ParameterWrapper> mapList) {
        return this.omopObservation.getSize(mapList);
    }

    @Override
    public Long getSize() {
        return this.omopObservation.getSize();
    }

    @Override
    public void searchWithoutParams(int fromIndex, int toIndex, List<IBaseResource> listResources,
                                    List<String> includes, String sort) {
        this.omopObservation.searchWithoutParams(fromIndex, toIndex, listResources, includes, sort);
    }

    @Override
    public void searchWithParams(int fromIndex, int toIndex, List<ParameterWrapper> paramList,
                                 List<IBaseResource> listResources, List<String> includes, String sort) {
        this.omopObservation.searchWithParams(fromIndex, toIndex, paramList, listResources, includes, sort);
    }

    @Override
    public List<ParameterWrapper> mapParameter(String parameter, Object value, boolean or) {
        return this.omopObservation.mapParameter(parameter, value,or);
    }

    @Override
    public FObservationView constructOmop(Long omopId, DiagnosticReport fhirResource) {
        return null;
    }
}
