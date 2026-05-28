package com.czertainly.np.email.service;

import com.czertainly.api.model.client.attribute.RequestAttribute;
import com.czertainly.api.model.common.attribute.common.BaseAttribute;
import com.czertainly.api.model.common.attribute.common.DataAttribute;

import java.util.List;

public interface AttributeService {

    List<BaseAttribute> getAttributes(String kind);

    boolean validateAttributes(String kind, List<RequestAttribute> attributes);

    List<DataAttribute> listMappingAttributes(String kind);

}
