package io.github.muer.persistence;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IamMapperContractTest {
    @Test
    void every_packaged_mapper_parses_and_registers_its_statements() throws Exception {
        var configuration = new Configuration();
        for (String resource : List.of(
                "mapper/iam/IamSessionMapper.xml",
                "mapper/iam/IamUserMapper.xml",
                "mapper/iam/IamIdentityMapper.xml",
                "mapper/iam/IamLoginEventMapper.xml",
                "mapper/iam/IamAuthorizationVersionMapper.xml",
                "mapper/iam/IamAuthorizationProfileMapper.xml",
                "mapper/iam/IamPermissionTemplateVersionMapper.xml",
                "mapper/iam/IamPermissionTemplateQueryMapper.xml",
                "mapper/iam/IamOverviewMapper.xml",
                "mapper/iam/IamAuditMapper.xml")) {
            try (var reader = Resources.getResourceAsReader(resource)) {
                new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamLoginEventMapper.insert"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamSessionMapper.touch"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamUserMapper.findPage"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamUserMapper.search"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamAuthorizationProfileMapper.search"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamSessionMapper.search"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamPermissionTemplateQueryMapper.listPermissions"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamOverviewMapper.load"));
        assertTrue(configuration.hasStatement(
                "io.github.muer.persistence.mapper.IamAuditMapper.findEvents"));
    }
}
