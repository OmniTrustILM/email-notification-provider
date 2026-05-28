package com.czertainly.np.email.service;

import com.czertainly.api.model.client.attribute.RequestAttributeDto;
import com.czertainly.api.model.common.attribute.v2.BaseAttribute;
import com.czertainly.api.model.common.attribute.v2.DataAttribute;

import java.util.List;

public interface AttributeService {

    List<BaseAttribute> getAttributes(String kind);

    boolean validateAttributes(String kind, List<RequestAttributeDto> attributes);

    List<DataAttribute> listMappingAttributes(String kind);

}
