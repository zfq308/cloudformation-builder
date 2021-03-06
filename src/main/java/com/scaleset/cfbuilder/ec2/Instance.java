package com.scaleset.cfbuilder.ec2;

import com.scaleset.cfbuilder.annotations.Type;
import com.scaleset.cfbuilder.core.Resource;

@Type("AWS::EC2::Instance")
public interface Instance extends Resource {

    Instance availabilityZone(Object... values);

    Instance instanceType(Object value);

    Instance imageId(Object value);

    Instance instanceProfile(Object value);

    Instance keyName(Object value);

    Instance securityGroupIds(Object... values);

    default Instance name(String name) {
        tag("Name", name).propagateAtLaunch();
        return this;
    }

    Instance subnetId(Object subnetId);

    Instance userData(Object userData);

}
