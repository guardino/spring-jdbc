package org.cloudfoundry.samples.music.web;

import org.cloudfoundry.samples.music.config.security.CORSConfig;
import org.cloudfoundry.samples.music.domain.DataObject;
import org.cloudfoundry.samples.music.domain.tools.ColumnRecorder;
import org.cloudfoundry.samples.music.repository.DataRepository;
import org.cloudfoundry.samples.music.tools.ConvertingTools;
import org.cloudfoundry.samples.music.tools.HttpCSVUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping
@EnableResourceServer
public class MaterialController
        extends ResourceServerConfigurerAdapter
{

    private static final Logger logger = LoggerFactory.getLogger(MaterialController.class);

    @Override
    public void configure(ResourceServerSecurityConfigurer resource) throws Exception{
        resource.resourceId("openid").stateless(true);
    }

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .addFilterBefore(new CORSConfig(), ChannelProcessingFilter.class)
                .authorizeRequests().anyRequest().authenticated();
    }

    @Autowired
    DataRepository repository;

    @Autowired
    ColumnRecorder columnRecorder;

    @Value("${my.default.table}")
    String table;

    @RequestMapping(method = RequestMethod.GET)
    public List<Map<String, Object>> getAll() {
        return repository.findAll(table);
    }

    @RequestMapping(value = "/columns", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> columns() {
        return columnRecorder.getColumns();
    }

    @RequestMapping(
            value = "/material",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> getMaterials(@RequestParam("query") String query){
        if(!query.equals("")) {
            logger.info("Searching for '" + query + "'");
            return repository.fuzzySearch(query, null, table);
        }
        return new ArrayList<Map<String, Object>>();
    }

    @RequestMapping(value="/material", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> add(@RequestBody String json) {
        HashMap<String, Object> map = ConvertingTools.JSONStringToHashMap(json);
        DataObject data = new DataObject(map);
        repository.save(data, table);
        logger.info("Adding object " + data.getId());
        try {
            return repository.findOne(data, table);
        } catch (EmptyResultDataAccessException e) {
            logger.error("Can not find data at table --"+table+"-- : " + data);
        }
        return new HashMap<>();
    }

    @RequestMapping(value="/material",method = RequestMethod.PUT)
    public Map<String, Object> update(@RequestBody String json) {
        HashMap<String, Object> map = ConvertingTools.JSONStringToHashMap(json);
        DataObject data = new DataObject(map);
        try {
            repository.updateItem(data, table);
            logger.info("Updating object " + data.getId());
        } catch (SQLDataException e) {
            e.printStackTrace();
        }
        try {
            return repository.findOne(data, table);
        } catch (EmptyResultDataAccessException e) {
            logger.error("Can not find data at table --"+table+"-- : " + data);
        }
        return new HashMap<>();
    }

    @RequestMapping(value = "/material/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getById(@PathVariable String id) {
        logger.info("Getting object " + id);
        try {
            return repository.findOne(id, table);
        } catch (EmptyResultDataAccessException e) {
            logger.error("Can not find data at table --"+table+"-- : " + id);
        }
        return new HashMap<>();
    }

    @RequestMapping(value = "/material/{id}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    public String deleteById(@PathVariable String id) {
        try {
            repository.deleteItem(id, table);
            logger.info("Deleting object " + id);
        } catch (DataAccessException e) {
            logger.error("Can not find data at table --"+table+"-- : " + id);
        }
        return "Deleted";
    }

    @RequestMapping(value = "/appendcsv", method = RequestMethod.POST)
    public String handleFileAppend(@RequestParam("file") MultipartFile multipart, RedirectAttributes redirectAttributes) {
        String res = "Success";

        HttpCSVUtils csvUtil = new HttpCSVUtils(multipart);
        List<String> rows = csvUtil.getRows();

        try {
            columnRecorder.addColumns(csvUtil.getLabels());
        } catch (SQLDataException e) { e.printStackTrace(); }

        logger.info("Import csv file: " + multipart.getOriginalFilename());
        int items = 0;
        for(int i=0; i<rows.size(); i++) {
            HashMap<String, Object> dataMap = csvUtil.getRowDataMap(i);
            DataObject data = new DataObject(dataMap);
            repository.save(data, table);
            logger.info("Adding object: " + data.getId());
            items++;
        }
        res += " : add " + items + " albums.";
        return res;
    }

    @RequestMapping(value = "/importcsv", method = RequestMethod.POST)
    public String handleFileImport(@RequestParam("file") MultipartFile multipart, RedirectAttributes redirectAttributes) {
        String res = "Success";

        HttpCSVUtils csvUtil = new HttpCSVUtils(multipart);
        List<String> rows = csvUtil.getRows();

        repository.deleteAll(table);
        columnRecorder.deleteAllColumns();

        try {
            columnRecorder.addColumn("id");
            columnRecorder.addColumns(csvUtil.getLabels());
        } catch (SQLDataException e) { e.printStackTrace(); }

        logger.info("Import csv file: " + multipart.getOriginalFilename());
        int items = 0;
        for(int i=0; i<rows.size(); i++) {
            HashMap<String, Object> dataMap = csvUtil.getRowDataMap(i);
            DataObject data = new DataObject(dataMap);
            repository.save(data, table);
            logger.info("Adding object: " + data.getId());
            items++;
        }
        res += " : import " + items + " albums.";
        return res;
    }

}