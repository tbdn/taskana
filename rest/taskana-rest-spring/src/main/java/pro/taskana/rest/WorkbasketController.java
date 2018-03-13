package pro.taskana.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pro.taskana.BaseQuery;
import pro.taskana.Workbasket;
import pro.taskana.WorkbasketAccessItem;
import pro.taskana.WorkbasketPermission;
import pro.taskana.WorkbasketQuery;
import pro.taskana.WorkbasketService;
import pro.taskana.WorkbasketSummary;
import pro.taskana.WorkbasketType;
import pro.taskana.exceptions.InvalidArgumentException;
import pro.taskana.exceptions.InvalidWorkbasketException;
import pro.taskana.exceptions.NotAuthorizedException;
import pro.taskana.exceptions.WorkbasketAlreadyExistException;
import pro.taskana.exceptions.WorkbasketInUseException;
import pro.taskana.exceptions.WorkbasketNotFoundException;
import pro.taskana.rest.resource.DistributionTargetResource;
import pro.taskana.rest.resource.WorkbasketAccessItemResource;
import pro.taskana.rest.resource.WorkbasketResource;
import pro.taskana.rest.resource.WorkbasketSummaryResource;
import pro.taskana.rest.resource.mapper.DistributionTargetListMapper;
import pro.taskana.rest.resource.mapper.WorkbasketAccessItemListMapper;
import pro.taskana.rest.resource.mapper.WorkbasketAccessItemMapper;
import pro.taskana.rest.resource.mapper.WorkbasketListMapper;
import pro.taskana.rest.resource.mapper.WorkbasketMapper;

/**
 * Controller for all {@link Workbasket} related endpoints.
 */
@RestController
@EnableHypermediaSupport(type = HypermediaType.HAL)
@RequestMapping(path = "/v1/workbaskets", produces = {MediaType.APPLICATION_JSON_VALUE})
public class WorkbasketController {

    private static final String LIKE = "%";
    private static final String NAME = "name";
    private static final String KEY = "key";
    private static final String DESCRIPTION = "description";
    private static final String OWNER = "owner";
    private static final String TYPE = "type";
    private static final String DESC = "desc";

    @Autowired
    private WorkbasketService workbasketService;

    @Autowired
    private WorkbasketMapper workbasketMapper;

    @Autowired
    private WorkbasketListMapper workbasketListMapper;

    @Autowired
    private DistributionTargetListMapper distributionTargetListMapper;

    @Autowired
    private WorkbasketAccessItemListMapper accessItemListMapper;

    @Autowired
    private WorkbasketAccessItemMapper workbasketAccessItemMapper;

    @GetMapping
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<Resources<WorkbasketSummaryResource>> getWorkbaskets(
        @RequestParam(value = "sortBy", defaultValue = "name", required = false) String sortBy,
        @RequestParam(value = "order", defaultValue = "asc", required = false) String order,
        @RequestParam(value = "name", required = false) String name,
        @RequestParam(value = "nameLike", required = false) String nameLike,
        @RequestParam(value = "key", required = false) String key,
        @RequestParam(value = "keyLike", required = false) String keyLike,
        @RequestParam(value = "descLike", required = false) String descLike,
        @RequestParam(value = "owner", required = false) String owner,
        @RequestParam(value = "ownerLike", required = false) String ownerLike,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "requiredPermission", required = false) String requiredPermission) {
        try {
            WorkbasketQuery query = workbasketService.createWorkbasketQuery();
            addSortingToQuery(query, sortBy, order);
            addAttributeFilter(query, name, nameLike, key, keyLike, descLike, owner, ownerLike, type);
            addAuthorizationFilter(query, requiredPermission);
            List<WorkbasketSummary> workbasketSummaries = query.list();
            Resources<WorkbasketSummaryResource> workbasketListResource = workbasketListMapper
                .toResource(workbasketSummaries);
            return new ResponseEntity<>(workbasketListResource, HttpStatus.OK);
        } catch (InvalidArgumentException ex) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/{workbasketId}")
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<WorkbasketResource> getWorkbasket(@PathVariable(value = "workbasketId") String workbasketId) {
        ResponseEntity<WorkbasketResource> result;
        try {
            Workbasket workbasket = workbasketService.getWorkbasket(workbasketId);
            result = new ResponseEntity<>(workbasketMapper.toResource(workbasket), HttpStatus.OK);
        } catch (WorkbasketNotFoundException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return result;
    }

    @DeleteMapping(path = "/{workbasketId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> deleteWorkbasket(@PathVariable(value = "workbasketId") String workbasketId) {
        ResponseEntity<?> result = ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        try {
            workbasketService.deleteWorkbasket(workbasketId);
        } catch (WorkbasketNotFoundException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (WorkbasketInUseException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = ResponseEntity.status(HttpStatus.LOCKED).build();
        } catch (InvalidArgumentException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        return result;
    }

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<WorkbasketResource> createWorkbasket(@RequestBody WorkbasketResource workbasketResource) {
        try {
            Workbasket workbasket = workbasketMapper.toModel(workbasketResource);
            workbasket = workbasketService.createWorkbasket(workbasket);
            return new ResponseEntity<>(workbasketMapper.toResource(workbasket), HttpStatus.CREATED);
        } catch (InvalidWorkbasketException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (WorkbasketAlreadyExistException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PutMapping(path = "/{workbasketId}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<WorkbasketResource> updateWorkbasket(
        @PathVariable(value = "workbasketId") String workbasketId,
        @RequestBody WorkbasketResource workbasketResource) {
        ResponseEntity<WorkbasketResource> result;
        try {
            if (workbasketId.equals(workbasketResource.workbasketId)) {
                Workbasket workbasket = workbasketMapper.toModel(workbasketResource);
                workbasket = workbasketService.updateWorkbasket(workbasket);
                result = ResponseEntity.ok(workbasketMapper.toResource(workbasket));
            } else {
                throw new InvalidWorkbasketException(
                    "Target-WB-ID('" + workbasketId
                        + "') is not identical with the WB-ID of to object which should be updated. ID=('"
                        + workbasketResource.getId() + "')");
            }
        } catch (InvalidWorkbasketException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (WorkbasketNotFoundException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        return result;
    }

    @GetMapping(path = "/{workbasketId}/workbasketAccessItems")
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<Resources<WorkbasketAccessItemResource>> getWorkbasketAccessItems(
        @PathVariable(value = "workbasketId") String workbasketId) {

        ResponseEntity<Resources<WorkbasketAccessItemResource>> result;
        try {
            List<WorkbasketAccessItem> accessItems = workbasketService.getWorkbasketAccessItems(workbasketId);
            Resources<WorkbasketAccessItemResource> accessItemListResource = accessItemListMapper
                .toResource(workbasketId, accessItems);
            result = new ResponseEntity<>(accessItemListResource, HttpStatus.OK);
        } catch (NotAuthorizedException e) {
            result = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return result;
    }

    @PutMapping(value = "/{workbasketId}/workbasketAccessItems")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Resources<WorkbasketAccessItemResource>> setWorkbasketAccessItems(
        @PathVariable(value = "workbasketId") String workbasketId,
        @RequestBody List<WorkbasketAccessItemResource> workbasketAccessResourceItems) {
        try {
            if (workbasketAccessResourceItems == null) {
                throw new InvalidArgumentException("Can´t create something with NULL body-value.");
            }

            List<WorkbasketAccessItem> wbAccessItems = new ArrayList<>();
            workbasketAccessResourceItems.forEach(item -> wbAccessItems.add(workbasketAccessItemMapper.toModel(item)));
            workbasketService.setWorkbasketAccessItems(workbasketId, wbAccessItems);

            List<WorkbasketAccessItem> updatedWbAccessItems = workbasketService.getWorkbasketAccessItems(workbasketId);
            Resources<WorkbasketAccessItemResource> accessItemListResource = accessItemListMapper
                .toResource(workbasketId, updatedWbAccessItems);

            return new ResponseEntity<>(accessItemListResource, HttpStatus.OK);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        } catch (InvalidArgumentException | NullPointerException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(path = "/{workbasketId}/distributiontargets")
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<Resources<DistributionTargetResource>> getDistributionTargets(
        @PathVariable(value = "workbasketId") String workbasketId) {

        ResponseEntity<Resources<DistributionTargetResource>> result;
        try {
            List<WorkbasketSummary> distributionTargets = workbasketService.getDistributionTargets(workbasketId);
            Resources<DistributionTargetResource> distributionTargetListResource = distributionTargetListMapper
                .toResource(workbasketId, distributionTargets);
            result = new ResponseEntity<>(distributionTargetListResource, HttpStatus.OK);
        } catch (WorkbasketNotFoundException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            result = new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        return result;
    }

    @PutMapping(path = "/{workbasketId}/distributiontargets")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<Resources<DistributionTargetResource>> setDistributionTargetsForWorkbasketId(
        @PathVariable(value = "workbasketId") String sourceWorkbasketId,
        @RequestBody List<String> targetWorkbasketIds) {
        try {
            workbasketService.setDistributionTargets(sourceWorkbasketId, targetWorkbasketIds);

            List<WorkbasketSummary> distributionTargets = workbasketService.getDistributionTargets(sourceWorkbasketId);
            Resources<DistributionTargetResource> distributionTargetListResource = distributionTargetListMapper
                .toResource(sourceWorkbasketId, distributionTargets);

            return new ResponseEntity<>(distributionTargetListResource, HttpStatus.OK);
        } catch (WorkbasketNotFoundException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (NotAuthorizedException e) {
            TransactionInterceptor.currentTransactionStatus().setRollbackOnly();
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    private void addAuthorizationFilter(WorkbasketQuery query, String requiredPermission)
        throws InvalidArgumentException {
        if (requiredPermission == null) {
            return;
        }

        for (String authorization : Arrays.asList(requiredPermission.split(","))) {
            switch (authorization.trim()) {
                case "READ":
                    query.callerHasPermission(WorkbasketPermission.READ);
                    break;
                case "OPEN":
                    query.callerHasPermission(WorkbasketPermission.OPEN);
                    break;
                case "APPEND":
                    query.callerHasPermission(WorkbasketPermission.APPEND);
                    break;
                case "TRANSFER":
                    query.callerHasPermission(WorkbasketPermission.TRANSFER);
                    break;
                case "DISTRIBUTE":
                    query.callerHasPermission(WorkbasketPermission.DISTRIBUTE);
                    break;
                case "CUSTOM_1":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_1);
                    break;
                case "CUSTOM_2":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_2);
                    break;
                case "CUSTOM_3":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_3);
                    break;
                case "CUSTOM_4":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_4);
                    break;
                case "CUSTOM_5":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_5);
                    break;
                case "CUSTOM_6":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_6);
                    break;
                case "CUSTOM_7":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_7);
                    break;
                case "CUSTOM_8":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_8);
                    break;
                case "CUSTOM_9":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_9);
                    break;
                case "CUSTOM_10":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_10);
                    break;
                case "CUSTOM_11":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_11);
                    break;
                case "CUSTOM_12":
                    query.callerHasPermission(WorkbasketPermission.CUSTOM_12);
                    break;
                default:
                    throw new InvalidArgumentException("Unknown authorization '" + authorization + "'");
            }
        }
    }

    private void addSortingToQuery(WorkbasketQuery query, String sortBy, String order) throws IllegalArgumentException {
        BaseQuery.SortDirection sortDirection = getSortDirection(order);

        switch (sortBy) {
            case NAME:
                query.orderByName(sortDirection);
                break;
            case KEY:
                query.orderByKey(sortDirection);
                break;
            case DESCRIPTION:
                query.orderByDescription(sortDirection);
                break;
            case OWNER:
                query.orderByOwner(sortDirection);
                break;
            case TYPE:
                query.orderByType(sortDirection);
                break;
            default:
                throw new IllegalArgumentException("Unknown order '" + sortBy + "'");
        }
    }

    private BaseQuery.SortDirection getSortDirection(String order) {
        if (order.equals(DESC)) {
            return BaseQuery.SortDirection.DESCENDING;
        }
        return BaseQuery.SortDirection.ASCENDING;
    }

    private void addAttributeFilter(WorkbasketQuery query,
        String name, String nameLike,
        String key, String keyLike,
        String descLike, String owner,
        String ownerLike, String type) throws InvalidArgumentException {
        if (name != null) {
            query.nameIn(name);
        }
        if (nameLike != null) {
            query.nameLike(LIKE + nameLike + LIKE);
        }
        if (key != null) {
            query.keyIn(key);
        }
        if (keyLike != null) {
            query.keyLike(LIKE + keyLike + LIKE);
        }
        if (owner != null) {
            query.ownerIn(owner);
        }
        if (ownerLike != null) {
            query.ownerLike(LIKE + ownerLike + LIKE);
        }
        if (descLike != null) {
            query.descriptionLike(LIKE + descLike + LIKE);
        }
        if (type != null) {
            switch (type) {
                case "PERSONAL":
                    query.typeIn(WorkbasketType.PERSONAL);
                    break;
                case "GROUP":
                    query.typeIn(WorkbasketType.GROUP);
                    break;
                case "CLEARANCE":
                    query.typeIn(WorkbasketType.CLEARANCE);
                    break;
                case "TOPIC":
                    query.typeIn(WorkbasketType.TOPIC);
                    break;
                default:
                    throw new InvalidArgumentException("Unknown Workbaskettype '" + type + "'");
            }
        }
    }
}