package com.otilm.np.email.api;

import com.otilm.api.interfaces.connector.InfoController;
import com.otilm.api.model.client.connector.InfoResponse;
import com.otilm.api.model.core.connector.FunctionGroupCode;
import com.otilm.np.email.EndpointsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class InfoControllerImpl implements InfoController {
    private static final Logger logger = LoggerFactory.getLogger(InfoControllerImpl.class);

    @Autowired
    public void setEndpointsListener(EndpointsListener endpointsListener) {
        this.endpointsListener = endpointsListener;
    }

    private EndpointsListener endpointsListener;

    @Override
    public List<InfoResponse> listSupportedFunctions() {
        logger.debug("Listing the end points for Email Notification Provider");
        List<String> kinds = List.of("EMAIL");
        List<InfoResponse> functions = new ArrayList<>();
        functions.add(new InfoResponse(
                kinds,
                FunctionGroupCode.NOTIFICATION_PROVIDER,
                endpointsListener.getEndpoints(FunctionGroupCode.NOTIFICATION_PROVIDER))
        );

        return functions;
    }
}