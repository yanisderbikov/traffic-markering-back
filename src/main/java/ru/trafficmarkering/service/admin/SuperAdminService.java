package ru.trafficmarkering.service.admin;

import ru.trafficmarkering.dto.admin.AdminUserDTO;
import ru.trafficmarkering.dto.admin.AssignRoleRequestDTO;

import java.util.List;

public interface SuperAdminService {

    List<AdminUserDTO> users();

    AdminUserDTO assignRole(AssignRoleRequestDTO request);
}
