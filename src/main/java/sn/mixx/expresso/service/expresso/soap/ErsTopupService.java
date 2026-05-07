package sn.mixx.expresso.service.expresso.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

/**
 * SEI (Service Endpoint Interface) pour l'API SOAP Expresso ERS TopupService.
 *
 * IMPORTANT: le targetNamespace est un placeholder.
 * Dès que l'accès VPN est disponible, récupérer le WSDL réel :
 *   curl http://10.71.47.17:8913/topupservice/service?wsdl
 * et mettre à jour targetNamespace avec la valeur exacte du WSDL.
 * Un namespace incorrect produit des SOAPFaultException au runtime même si
 * l'endpoint URL est correct.
 */
@WebService(
    name = "ErsTopupService",
    targetNamespace = "http://topupservice.expresso.mixx.sn/"
)
@SOAPBinding(
    style = SOAPBinding.Style.DOCUMENT,
    use = SOAPBinding.Use.LITERAL,
    parameterStyle = SOAPBinding.ParameterStyle.WRAPPED
)
public interface ErsTopupService {

    @WebMethod(operationName = "requestTopup")
    RequestTopupResponse requestTopup(
        @WebParam(name = "requestTopup") RequestTopupRequest request
    );

    @WebMethod(operationName = "getTransactionStatus")
    GetTransactionStatusResponse getTransactionStatus(
        @WebParam(name = "getTransactionStatus") GetTransactionStatusRequest request
    );

    @WebMethod(operationName = "requestPrincipalInformation")
    RequestPrincipalInformationResponse requestPrincipalInformation(
        @WebParam(name = "requestPrincipalInformation") RequestPrincipalInformationRequest request
    );
}
